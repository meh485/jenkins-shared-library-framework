def call(Map config) {
    pipeline {
        agent any
        environment {
            EMAIL_RECIPIENTS        =   config.email_recipients
            CHECKMARX_CREDENTIAL_ID =   config.checkmarx_credential_id
            CHECKMARX_HOST          =   config.checkmarx_host
        }
        stages {
            stage('Cleanup & Checkout'){
                steps{
                    echo 'Cleaning workspace'
                    deleteDir()
                    checkout scm
                }
            }
            stage('Execute CheckMarx Scan'){
                steps {
                    step([$class: 'CxScanBuilder', comment: '',
                            projectName: config.projectName ,
                            comment: config.comment,
                            teamPath: config.teamPath,
                            useOwnServerCredentials: true,
                            credentialsId: "$CHECKMARX_CREDENTIAL_ID",
                            sastEnabled: true,
                            avoidDuplicateProjectScans: true,
                            excludeFolders: config.excludeFolders,
                            //excludeOpenSourceFolders: config.excludeOpenSourceFolders,
                            exclusionsSetting: 'job',
                            failBuildOnNewResults: true,
                            failBuildOnNewSeverity: 'HIGH',
                            filterPattern: '''!**/_cvs/**/*, !**/.svn/**/*,   !**/.hg/**/*,   !**/.git/**/*,  !**/.bzr/**/*, !**/bin/**/*,
                                            !**/obj/**/*,  !**/backup/**/*, !**/.idea/**/*, !**/build/**/*, !**/ETL/**/*, !**/install/**/*, !**/XSD/**/*
                                            !**/*.DS_Store, !**/*.ipr,     !**/*.iws,
                                            !**/*.bak,     !**/*.tmp,       !**/*.aac,      !**/*.aif,      !**/*.iff,     !**/*.m3u, !**/*.mid, !**/*.mp3,
                                            !**/*.mpa,     !**/*.ra,        !**/*.wav,      !**/*.wma,      !**/*.3g2,     !**/*.3gp, !**/*.asf, !**/*.asx,
                                            !**/*.avi,     !**/*.flv,       !**/*.mov,      !**/*.mp4,      !**/*.mpg,     !**/*.rm,  !**/*.swf, !**/*.vob,
                                            !**/*.wmv,     !**/*.bmp,       !**/*.gif,      !**/*.jpg,      !**/*.png,     !**/*.psd, !**/*.tif, !**/*.swf,
                                            !**/*.jar,     !**/*.zip,       !**/*.rar,      !**/*.exe,      !**/*.dll,     !**/*.pdb, !**/*.7z,  !**/*.gz,
                                            !**/*.tar.gz,  !**/*.tar,       !**/*.gz,       !**/*.ahtm,     !**/*.ahtml,   !**/*.fhtml, !**/*.hdm,
                                            !**/*.hdml,    !**/*.hsql,      !**/*.ht,       !**/*.hta,      !**/*.htc,     !**/*.htd, !**/*.war, !**/*.ear,
                                            !**/*.htmls,   !**/*.ihtml,     !**/*.mht,      !**/*.mhtm,     !**/*.mhtml,   !**/*.ssi, !**/*.stm,
                                            !**/*.stml,    !**/*.ttml,      !**/*.txn,      !**/*.xhtm,     !**/*.xhtml,   !**/*.class, !**/*.iml,
                                            !Checkmarx/Reports/*.*, !**/*.xml,!**/*.xsl,!**/*.properties,!**/*.war,
                                            !**/*.bat, !**/*.sh, !**/*.txt, !**/*.sql, !**/*.7z, !**/*.doc, !**/*.docx, !**/*.css, !**/*.less, !**/*.jsp,
                                            !**/*.html, !**/*.htm, !**/*.svg, !**/*.json, !**/*.thmx, !**/*.xsd, !**/*.tag, !**/*.tld, !**/*.iap_xml, !**/*.xls, !**/*.md''',
                            fullScanCycle: 10,
                            includeOpenSourceFolders: '',
                            incremental: false,
                            osaArchiveIncludePatterns: '*.zip, *.war, *.ear, *.tgz',
                            osaInstallBeforeScan: false,
                            preset: '100001', //BroadridgeAutomatedHM
                            //If the preset is not specified, the default preset for a new project will be used.
                            serverUrl: "$CHECKMARX_HOST",
                            sourceEncoding: '1',
                            waitForResultsEnabled: true,
                            vulnerabilityThresholdEnabled: true,
                            highThreshold: 0,
                            vulnerabilityThresholdResult: 'FAILURE',
                            generatePDFReport: true
                        ])
                }
            }
            stage('Create Archive'){
                steps{
                    script{
                        sh 'mkdir -p Checkmarx/Reports'
                    }
                }
            }
        }
        post {
            always {
                script {
                    try {
                        zip zipFile: 'Checkmarx_Reports.zip', archive: true, dir: 'Checkmarx/Reports'
                        archiveArtifacts artifacts: 'Checkmarx_Reports.zip', fingerprint: true
                    } catch (any) {
                        currentBuild.result = "FAILURE"
                        buildResult = "FAILURE"
                    } finally {
                        echo "Build result of ${env.JOB_NAME}: ${currentBuild.result}"
                        stage('Send results in email') {
                            emailext (
                                body: '${SCRIPT,template="groovy-html.template"}',
                                attachLog: false,
                                compressLog: false,
                                mimeType: 'text/html',
                                subject: '[Jenkins] CheckMarx report',
                                to: "${EMAIL_RECIPIENTS}",
                                attachmentsPattern: 'Checkmarx/Reports/Checkmarx_Reports.zip'
                            )
                        }
                    }
                }
            }
        }
    }
}