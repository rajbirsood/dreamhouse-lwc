def call(body) {
    def pipelineParams= [:]
    def serviceVersionMap = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

	pipeline {
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        }
        agent {
            kubernetes {
                label 'ic-sfdx-builder'
                defaultContainer 'jnlp'
                yaml """
apiVersion: v1
kind: Pod
metadata:
  labels:
    name: sfdx-builder
spec:
  containers:
  - name: ic-tag-builder
    image: art01-ic-devops.jfrog.io/ic-tag-builder:1.x
    command:
    - cat
    tty: true
  - name: ic-sfdx-builder
    image: art01-ic-devops.jfrog.io/ic-sfdx-builder:1.10.1
    command:
    - cat
    tty: true
    volumeMounts:
    - mountPath: /var/run/docker.sock
      name: host-file
  - name: ic-utility-builder
    image: art01-ic-devops.jfrog.io/ic-utility-builder:1.1.1
    command:
    - cat
    tty: true
    volumeMounts:
    - mountPath: /var/run/docker.sock
      name: host-file 
  volumes:
  - name: host-file
    hostPath:
      path: /var/run/docker.sock
      type: File
  imagePullSecrets:
  - name: devops-docker-secret
  """
            }
        }
        parameters {

            choice(
                name: 'PRODUCT', 
                choices: "DC\nCLM\nCPQ\nZEUS\nECHOSIGN-CLM\nDOCUSIGN-CLM\nCONTENT-CODE\nDOCUSIGN-API\nBILLING\nMEB", 
                description: 'Select PRODUCT to be used from dropdown list, Ex: CPQ, ZEUS, CLM, DC, MEB'
            )
            booleanParam(
                name: 'DEPLOY_ALL', 
                defaultValue: true, 
                description: 'Enable this for all SRC deployment on DEV scratch ORG'
            )
            booleanParam(
                name: 'RUN_UNIT_TEST', 
                defaultValue: false, 
                description: 'Enable this to run apex unit test after deployment to DEV scratch org'
            )
            booleanParam(
                name: 'IMPORT_TEST_DATA', 
                defaultValue: false, 
                description: 'Enable this to import test data after deployment to DEV scratch org'
            )
            string(
                name: 'TESTLEVEL', 
                defaultValue: 'RunLocalTests', 
                description: 'Permissible values are: RunLocalTests, RunAllTestsInOrg'
            )            
            booleanParam(
                name: 'RUN_BVT_TEST', 
                defaultValue: false, 
                description: 'Enable this to run BVT'
            )
            choice(
                name: 'NAMESPACE', 
                choices: "\nApttus\nApttus_CIM\nApttus_WebStore\nApttus_CPQAdmin\nApttus_Config2\nApttus_Echosign\nApttus_CMDSign\nApttus_DocuApi\nApttus_Content\nApttus_Billing", 
                description: 'Select Namespace to be used from dropdown list to create scratch org and package, Ex: For CPQ - Apttus_config2'
            )
            string(
                name: 'API_VERSION', 
                defaultValue: '47.0', 
                description: 'API version to be used.If empty, default defined in sfdx-project.json will be used'
            )  
            string(
                name: 'USER_EMAIL', 
                defaultValue: '', 
                description: 'User email id is associated with Scratch org'
            )                        
            string(
                name: 'SCRATCH_ORG_VALIDITY_DAYS', 
                defaultValue: '30', 
                description: 'Scratch org validity in days.MIN 7 days, MAX 30 days' 
            )           
            string(
                name: 'DEV_SCRATCH_ORGNAME', 
                defaultValue: 'ApttusDev', 
                description: 'Dev Scratch Organization name. Default read from build scratch org definition file'
            )
            string(
                name: 'DEV_SCRATCH_USERNAME', 
                defaultValue: '', 
                description: 'Dev Scratch org username to create. Example test-scratchuserDev@apttus.com.This field is Required'
            )
            string(
                name: 'DEV_EDITION', 
                defaultValue: 'Developer', 
                description: 'Valid entries are Developer, Enterprise, Group, or Professional. Default read from scratch org definition file'
            )   
            booleanParam(
                name: 'INSTALL_DEPENDENT_PACKAGE_DEV_SCRATCH_ORG', 
                defaultValue: false, 
                description: 'Enable this to install dependent package on DEV scratch before deployment,Make sure you have separte DependentPackageList.properties file in your Repo with package details'
            ) 
            booleanParam(
                name: 'DEPLOY_ADDITIONAL_COMPONENT_ON_DEV_SCRATCH_ORG', 
                defaultValue: false, 
                description: 'deploy additional component on DEV scratch for BVT, unit test'
            )  

            booleanParam(
                name: 'EXECUTE_CODE', 
                defaultValue: false, 
                description: 'Enable this to execute the Code snippet using ToolingAPI'
            )    

            booleanParam(
                name: 'ENABLE_PACKAGE_CREATION', 
                defaultValue: false, 
                description: 'Enable this to create and install unlocked, Manage Package on QA scratch Org'
            )    

            string(
                name: 'PACKAGE_NAME', 
                defaultValue: '', 
                description: 'package name you want to create This field is Required'
            )
            string(
                name: 'PACKAGE_TYPE', 
                defaultValue: 'Unlocked', 
                description: 'Type of package to create, valid entries are: Unlocked,locked, Manage'
            ) 
            string(
                name: 'VERSION_NUMBER', 
                defaultValue: '', 
                description: 'Package VersionNumber to use Example: 1.0.0.0 (MAJOR_VERSION.MINOR_VERSION.PATCH_VERSION.BUILD_VERSION)'
            )        
            string(
                name: 'WAIT_TIME', 
                defaultValue: '100', 
                description: 'Maximum Wait time in minutes to create a package'
            )

            booleanParam(
                name: 'CREATE_QA_SCRATCH_ORG', 
                defaultValue: false, 
                description: 'Enable this to create QA scratch Org'
            )  

            string(
                name: 'QA_SCRATCH_ORG_VALIDITY_DAYS', 
                defaultValue: '30', 
                description: 'Scratch org validity in days'
            )
            string(
                name: 'QA_SCRATCH_ORGNAME', 
                defaultValue: 'ApttusQA', 
                description: 'QA Scratch Organization name.Default read from Qa scratch org definition file'
            )
            string(
                name: 'QA_SCRATCH_USERNAME', 
                defaultValue: '', 
                description: 'QA Scratch org username to create. Example test-QAscratchuser@apttus.com.This field is Required'
            )
            string(
                name: 'QA_EDITION', 
                defaultValue: 'Enterprise', 
                description: 'Valid entries are Developer, Enterprise, Group, or Professional.Default read from Qa scratch org definition file'
            )
            booleanParam(
                name: 'INSTALL_DEPENDENT_PACKAGE_QA_SCRATCH_ORG', 
                defaultValue: false, 
                description: 'Enable this to install dependent package on QA scratch before installation of Manage/unlocked package, Make sure you have separte DependentPackageList.properties file in your Repo with package details'
            )  
            booleanParam(
                name: 'POST_INSTALL_DEPENDENT_PACKAGE_QA_SCRATCH_ORG', 
                defaultValue: false, 
                description: 'Enable this to install dependent package on QA scratch post installation of Manage/unlocked package, eg Signing packages for automation testing, Make sure you have separte PostDependentPackageList.properties file in your Repo with package details'
            )             
            booleanParam(
                name: 'DEPLOY_ADDITIONAL_COMPONENT_ON_QA_SCRATCH_ORG', 
                defaultValue: false, 
                description: 'deploy additional component on QA scratch for BVT, unit test, QA automation'
            )  
 
        }
     
        stages { 
            stage('Initialize'){
                steps {
	                script{

                        jenkinsFilePath="${pipelineParams.jenkinsFilePath}"
                        (jenkinsFilePath == "") ? jenkinsFilePath="./":""

                        copyGlobalLibraryScript(jenkinsFilePath,'sfdx/Makefile')
                        copyGlobalLibraryScript(jenkinsFilePath,'sfdx/make_script.sh')     
                        copyGlobalLibraryScript(jenkinsFilePath,'sfdx/devopsconfig.properties')
                        copyGlobalLibraryScript(jenkinsFilePath,'sfdx/dev-scratch-def.json')   
                        copyGlobalLibraryScript(jenkinsFilePath,'sfdx/qa-scratch-def.json')
                        copyGlobalLibraryScript(jenkinsFilePath,'sfdx/server.key')     
                    //  copyGlobalLibraryScript(jenkinsFilePath,'sfdx/sfdx-project.json')
                                                                    
	                    git_branch="${GIT_BRANCH}" 
                        def workspace = pwd()

						// read Parameters from build.properties from project specific repo
						buildPropertiesPath= "./build.properties"
                        buildProperties = readProperties file: buildPropertiesPath

                        // Dependent_PACKAGES_PATH from where we will install list of dependent packages on scratch org
                        //DEPENDENT_PACKAGES_PATH=buildProperties['Dependent_PACKAGES_PATH']
                        DEPENDENT_PACKAGES_PATH=params.PRODUCT + "DependentPackageList.properties" 
                        POST_DEPENDENT_PACKAGES_PATH = params.PRODUCT + "PostDependentPackageList.properties" 
                        echo "using ${DEPENDENT_PACKAGES_PATH} which has all dependent packages list to install on Scratch org" 
                        BVT_JOB_NAME=buildProperties['BVT_JOB_NAME']
                        echo "${BVT_JOB_NAME}"

                        CODE_SNIPPET_FILE=params.PRODUCT + "CodeSnippets.properties" 
                        echo "using ${CODE_SNIPPET_FILE} which has all the code snippets to execute using Tooling API" 
                        
                        // Team notification Channel
                        teams_NotificationChannel = buildProperties['TEAMS_NOTIFICATION_CHANNEL'] 

                        // read Parameters from devopsconfig.properties from Jenkins shared library
                        devopsconfigPath= "./devopsconfig.properties"
						devopsconfigProperties = readProperties file: devopsconfigPath
                        
						//sfdx project name 
                        PROJECT_NAME=devopsconfigProperties['SFDX_PROJECT_NAME']
                        PROJECT_PATH= "${workspace}" + File.separator + "${PROJECT_NAME}"
                        //PROJECT_PATH="${workspace}"
                        echo "PROJECT_PATH=${PROJECT_PATH}"

                        // INSTALLATION_KEY for installaing dependent packages on scratch org
                        INSTALLATION_KEY=devopsconfigProperties['INSTALLATION_KEY']

                        echo "Reading Build.Properties"  

                        SFDX_FOLDER_PATH=buildProperties[params.PRODUCT + '_SFDX_FOLDER_PATH']
                        echo "SFDX_FOLDER_PATH=${SFDX_FOLDER_PATH}"  
                        // Additional component path(relative path) to deploy on scratch org
                        DEV_ADDITIONAL_SRC_PATH=buildProperties[params.PRODUCT + '_DEV_ADDITIONAL_SRC_PATH']
                        QA_ADDITIONAL_SRC_PATH=buildProperties[params.PRODUCT + '_QA_ADDITIONAL_SRC_PATH']

                        //Check import test data path
                        IMPORT_TEST_DATA_PATH=buildProperties[params.PRODUCT + '_IMPORT_TEST_DATA_PATH']
                        echo "IMPORT_TEST_DATA_PATH=${IMPORT_TEST_DATA_PATH}"

                        // Check if src code is in SFDX FORMAT (in build properties file)
                        if(buildProperties[params.PRODUCT + '_IS_PROJECT_ALREADY_IN_SFDX_FORMAT']){ 
                            IS_PROJECT_ALREADY_IN_SFDX_FORMAT=buildProperties[params.PRODUCT + "_IS_PROJECT_ALREADY_IN_SFDX_FORMAT"].toLowerCase()
                        } else {
                            IS_PROJECT_ALREADY_IN_SFDX_FORMAT="false"
                        }
                        echo "IS_PROJECT_ALREADY_IN_SFDX_FORMAT: ${IS_PROJECT_ALREADY_IN_SFDX_FORMAT}" 

                        if (IS_PROJECT_ALREADY_IN_SFDX_FORMAT == "true")
                        {
                           echo "project is already SFDX format"   
                           PROJECT_PATH="${workspace}"
                           echo "PROJECT_PATH: ${PROJECT_PATH}"
                        }
                        // Check if deployment of src code is enabled in build properties file
                        if(buildProperties[params.PRODUCT + '_ENABLE_DEPLOY']){ 
                            enable_Deploy=buildProperties[params.PRODUCT + "_ENABLE_DEPLOY"].toLowerCase()
                        } else {
                            enable_Deploy="false"
                        }
                        // Check if User enable deploy in Jenkins parameter
                        if (params.DEPLOY_ALL) {
                            echo "Enabling Deployment using Jenkins parameter"
                            enable_Deploy="true"
                        } 
                        echo "enable_Deploy: ${enable_Deploy}" 

                        if (params.EXECUTE_CODE) {
                            echo "Enabling execution of given Code using Tooling API in the created ScratchOrg"
                            enable_execute_code="true"
                        }  else {
                            enable_execute_code="false"
                        }

                        //enable Unit Test on Scratch org
                        if(buildProperties[params.PRODUCT + '_ENABLE_UNIT_TEST']){ 
                            enable_Unit_Test=buildProperties[params.PRODUCT + "_ENABLE_UNIT_TEST"].toLowerCase()
                        } else {
                            enable_Unit_Test="false"
                        }
                        // Check if User enable unit test in Jenkins parameter
                        if (params.RUN_UNIT_TEST) {
                            echo "Enabling UNIT Test using Jenkins parameter"
                            enable_Unit_Test="true"
                        } 
                        echo "enable_Unit_Test: ${enable_Unit_Test}" 

                        // Check if Dependent packages need to be installed on Dev Scratch Org
                        if(buildProperties[params.PRODUCT + "_INSTALL_DEPENDENT_PACKAKES_ON_DEV_SCRATCH_ORG"]){ 
                            enable_Install_Dependent_Packages_DEV=buildProperties[params.PRODUCT + "_INSTALL_DEPENDENT_PACKAKES_ON_DEV_SCRATCH_ORG"].toLowerCase()
                        } else {
                            enable_Install_Dependent_Packages_DEV="false"
                        }
                        // Check if User enable Dependent packages on Dev Scratch in Jenkins parameter
                        if (params.INSTALL_DEPENDENT_PACKAGE_DEV_SCRATCH_ORG) {
                            echo "Enabling Installation on Dependent Package on Dev Org using Jenkins parameter"
                            enable_Install_Dependent_Packages_DEV="true"
                        }                         
                        echo "enable_Install_Dependent_Packages_DEV: ${enable_Install_Dependent_Packages_DEV}" 

                        // Check if Dependent packages need to be installed on QA Scratch Org
                        if(buildProperties[params.PRODUCT + "_INSTALL_DEPENDENT_PACKAKES_ON_QA_SCRATCH_ORG"]){ 
                            enable_Install_Dependent_Packages_QA=buildProperties[params.PRODUCT + "_INSTALL_DEPENDENT_PACKAKES_ON_QA_SCRATCH_ORG"].toLowerCase()
                        } else {
                            enable_Install_Dependent_Packages_QA="false"
                        }
                        // Check if User enable Dependent packages on QA Scratch in Jenkins parameter
                        if (params.INSTALL_DEPENDENT_PACKAGE_QA_SCRATCH_ORG) {
                            echo "Enabling Installation on Dependent Package on QA Org using Jenkins parameter"
                            enable_Install_Dependent_Packages_QA="true"
                        }                         
                        echo "enable_Install_Dependent_Packages_QA: ${enable_Install_Dependent_Packages_QA}" 

                        // Check if Dependent packages need to be installed on QA Scratch Org
                        if(buildProperties[params.PRODUCT + "_POST_INSTALL_DEPENDENT_PACKAKES_ON_QA_SCRATCH_ORG"]){ 
                            enable_Post_Install_Dependent_Packages_QA=buildProperties[params.PRODUCT + "_POST_INSTALL_DEPENDENT_PACKAKES_ON_QA_SCRATCH_ORG"].toLowerCase()
                        } else {
                            enable_Post_Install_Dependent_Packages_QA="false"
                        }
                        // Check if User enable Dependent packages on QA Scratch in Jenkins parameter
                        if (params.POST_INSTALL_DEPENDENT_PACKAGE_QA_SCRATCH_ORG) {
                            echo "Enabling POST Installation on Dependent Package on QA Org using Jenkins parameter"
                            enable_Post_Install_Dependent_Packages_QA="true"
                        }                         
                        echo "enable_Post_Install_Dependent_Packages_QA: ${enable_Post_Install_Dependent_Packages_QA}" 

                        // if Any additional components need to be deployed on Dev Scratch ORG
                        if(buildProperties[params.PRODUCT + "_ENABLE_DEPLOY_ADDITIONAL_COMPONENT_ON_DEV_SCRATCH_ORG"]){ 
                            enable_Deploy_Additional_Component_On_DEV=buildProperties[params.PRODUCT + "_ENABLE_DEPLOY_ADDITIONAL_COMPONENT_ON_DEV_SCRATCH_ORG"].toLowerCase()
                        } else {
                            enable_Deploy_Additional_Component_On_DEV="false"
                        }
                        // Check if User enable DEPLOY_ADDITIONAL_COMPONENT_ON_DEV_SCRATCH_ORG on Dev Scratch in Jenkins parameter
                        if (params.DEPLOY_ADDITIONAL_COMPONENT_ON_DEV_SCRATCH_ORG) {
                            echo "Enabling Deployment of Additional Components on Dev Org using Jenkins parameter"
                            enable_Deploy_Additional_Component_On_DEV="true"
                        }                         
                        echo "enable_Deploy_Additional_Component_On_DEV: ${enable_Deploy_Additional_Component_On_DEV}" 


                        // if Any additional components need to be deployed on QA Scratch ORG
                        if(buildProperties[params.PRODUCT + "_ENABLE_DEPLOY_ADDITIONAL_COMPONENT_ON_QA_SCRATCH_ORG"]){ 
                            enable_Deploy_Additional_Component_On_QA=buildProperties[params.PRODUCT + "_ENABLE_DEPLOY_ADDITIONAL_COMPONENT_ON_QA_SCRATCH_ORG"].toLowerCase()
                        } else {
                            enable_Deploy_Additional_Component_On_QA="false"
                        }
                        // Check if User enable DEPLOY_ADDITIONAL_COMPONENT_ON_QA_SCRATCH_ORG on QA Scratch in Jenkins parameter
                        if (params.DEPLOY_ADDITIONAL_COMPONENT_ON_QA_SCRATCH_ORG) {
                            echo "Enabling Deployment of Additional Components on QA Org using Jenkins parameter"
                            enable_Deploy_Additional_Component_On_QA="true"
                        }                         
                        echo "enable_Deploy_Additional_Component_On_DEV: ${enable_Deploy_Additional_Component_On_DEV}" 

                        // Check if deployment of test metadata is enabled in build properties file
                        if(buildProperties[params.PRODUCT + "_ENABLE_DEPLOY_TEST_METADATA"]){ 
                            enable_TestMetaData_Deploy=buildProperties[params.PRODUCT + "_ENABLE_DEPLOY_TEST_METADATA"].toLowerCase()
                        } else {
                            enable_TestMetaData_Deploy="false"
                        }
                        echo "enable_TestMetaData_Deploy: ${enable_TestMetaData_Deploy}" 

                        // Check if import test data to scratch org is enabled in build properties file
                        if(buildProperties[params.PRODUCT + "_ENABLE_IMPORT_TEST_DATA"]){ 
                            enable_Import_Test_Data=buildProperties[params.PRODUCT + "_ENABLE_IMPORT_TEST_DATA"].toLowerCase()
                        } else {
                            enable_Import_Test_Data="false"
                        }
                        echo "enable_Import_Test_Data: ${enable_Import_Test_Data} "

                        // Check if User enable Import test data in Jenkins parameter
                        if (params.IMPORT_TEST_DATA) {
                            echo "Enabling Import test data using Jenkins parameter"
                            enable_Import_Test_Data="true"
                        } 
                        echo "enable_Import_Test_Data: ${enable_Import_Test_Data}" 
                        //roject specific package sfdx-project.json Path
                        SFDX_PROJECT_JSON_FILE=buildProperties['SFDX_PROJECT_JSON_FILE']
                        SFDX_PROJECT_JSON_PATH="${workspace}"+ File.separator + "${SFDX_PROJECT_JSON_FILE}"
                        echo "SFDX_PROJECT_JSON_PATH: ${SFDX_PROJECT_JSON_PATH}"

                        //DEVHub Client ID and Client Secret consumed by BVT 
                        CLIENT_ID=devopsconfigProperties['CLIENT_ID'] 
                        CLIENT_SECRET=devopsconfigProperties['CLIENT_SECRET']
                        echo "${CLIENT_ID}"

                        //SCRATCH org details will be stored dynamically in this temp file. which will be pass 
                        // as paremeters to BVT and automation 
                        // Dev scratch org temp file
                        DEV_SCRATCH_ORG_DETAILS_TEMP_FILE=devopsconfigProperties['DEV_SCRATCH_ORG_DETAILS_TEMP_FILE']

                        // QA scratch org temp file
                        QA_SCRATCH_ORG_DETAILS_TEMP_FILE=devopsconfigProperties['QA_SCRATCH_ORG_DETAILS_TEMP_FILE']
                                            
                        // get build and Qa scratch definition file name from scratch.properties filr
                        DEV_SCRATCH_ORG_DEFINITION_FILE=devopsconfigProperties['DEV_SCRATCH_ORG_DEFINITION_FILE'] 
                        QA_SCRATCH_ORG_DEFINITION_FILE=devopsconfigProperties['QA_SCRATCH_ORG_DEFINITION_FILE']

                        // Dev Hub Authentication parameters          
                        echo "using devopsconfig.properties"
						CONSUMER_KEY=devopsconfigProperties['CONSUMER_KEY']
						HUB_USERNAME=devopsconfigProperties['HUB_USERNAME']
						JWT_KEY_FILE=devopsconfigProperties['JWT_KEY_FILE']

                        // Check if mandatory fields are there or not
                        if (!params.USER_EMAIL )
                        {
                            error("Please enter user email ID")
                        }

                    }//script                    
	            } //steps
		    } //stage

            stage('Authorize DevHub'){                
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                            sh """
                                echo "Authorizing to DevHUb"
                                echo "GIT_BRANCH: ${git_branch}"
                                echo "workspace: $workspace"
                                make authenticate-DevHub CONSUMER_KEY=${CONSUMER_KEY} HUB_USERNAME=${HUB_USERNAME} JWT_KEY_FILE=$workspace/${JWT_KEY_FILE}
                            """
                        //    runBVT(downstreamJob)
                        }                  
                    }//script                    
	            } //steps
		    }//stage   
            
            stage('Create SFDX project'){
            when { expression { return (IS_PROJECT_ALREADY_IN_SFDX_FORMAT == "false" )  } }    
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                    
                            sh """
                                echo "Creating SFDX based project"
                                echo "PROJECT_NAME: ${PROJECT_NAME}"
                                echo "WORKSPACE: $workspace"
                                make create-SFDX-Project PROJECT_NAME=${PROJECT_NAME} WORKSPACE=$workspace
                            """
                        }                  
                    }//script                    
	            } //steps
		    }//stage

            stage('Convert to SFDX format'){
            when { expression { return (IS_PROJECT_ALREADY_IN_SFDX_FORMAT == "false" )  } }    
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                    
                            sh """
                                echo "Converting src to SFDX format beforing creating package"                    
                                echo "GIT_BRANCH: ${git_branch}"
                                echo "workspace: $workspace"
                                make convert-to-SfdxFormat PROJECT_PATH=${PROJECT_PATH} SRC_PATH=$workspace/src
                            """
                        }                  
                    }//script                    
	            } //steps
		    }//stage

  
            stage('Create Dev Scratch Org'){
            when { expression { return ( enable_Deploy == "true") } }
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                    
                            sh """
                                echo "Creating buildScratchOrg for deploying and testing"
                                echo "SCRATCH_ORG_NAME: ${DEV_SCRATCH_ORGNAME}"
                                echo "DEV_SCRATCH_USERNAME: ${DEV_SCRATCH_USERNAME}"
                                echo "NAMESPACE: ${NAMESPACE}"
                                echo "API_VERSION: ${API_VERSION}"
                                echo "SCRATCH_ORG_DURATION: ${SCRATCH_ORG_VALIDITY_DAYS}"
                                echo "ORGNAME: ${DEV_SCRATCH_ORGNAME}"
                                echo "EDITION: ${DEV_EDITION}"
                                echo "PROJECT_PATH: ${PROJECT_PATH}"
                                echo "SCRATCH_DEFINITION_FILE_PATH: $workspace/${DEV_SCRATCH_ORG_DEFINITION_FILE}"
                                echo "HUB_USERNAME: ${HUB_USERNAME}"
                                echo "USER_EMAIL: ${USER_EMAIL}"
                                echo "devopsconfig.properties: $workspace/devopsconfig.properties"    
                                echo "SCRATCH_ORG_DURATION: ${SCRATCH_ORG_VALIDITY_DAYS}"
                                make create-scratchORG SCRATCH_ORG_NAME="${DEV_SCRATCH_ORGNAME}" SCRATCH_ORG_DURATION="${SCRATCH_ORG_VALIDITY_DAYS}" ORGNAME="${DEV_SCRATCH_ORGNAME}" EDITION="${DEV_EDITION}" PROJECT_PATH=${PROJECT_PATH} SCRATCH_DEFINITION_FILE_PATH=$workspace/${DEV_SCRATCH_ORG_DEFINITION_FILE} HUB_USERNAME="${HUB_USERNAME}" NAMESPACE="${NAMESPACE}" SCRATCH_ORG_DETAILS_TEMP_FILE=$workspace/"${DEV_SCRATCH_ORG_DETAILS_TEMP_FILE}" SCRATCH_USERNAME_TO_CREATE="${DEV_SCRATCH_USERNAME}" API_VERSION="${API_VERSION}" ADMIN_EMAIL="${USER_EMAIL}"
                            """
                            // Send dev scratch org details to Team channel
                            scratchOrgPropertieFilePath="./${DEV_SCRATCH_ORG_DETAILS_TEMP_FILE}.properties"
                            scratchDetails = readProperties file: scratchOrgPropertieFilePath
                            scratch_org_user=scratchDetails['username']
                            scratch_org_password=scratchDetails['password']
                            instanceUrl=scratchDetails['instanceUrl']
                            scratchorgDetails="INSTANCE_URL: ${instanceUrl} \r\n ${'\n'} SCRATCH_ORG_USER: ${scratch_org_user} \r\n ${'\n'} SCRATCH_ORG_PASSWORD: ${scratch_org_password} \r\n ${'\n'}"
                            echo "${scratchorgDetails}"
                            
                            sendNotifications("INFO","${teams_NotificationChannel}","Authorize DevHub", "DEV Scratch Org Details: \r\n ${'\n'} ${scratchorgDetails}")
                        }                  
                    }//script                    
	            } //steps
                post{
                    failure{
                        sendNotifications("Failure","${teams_NotificationChannel}","DEV Scratch org creation failed")
                    }
                }                 
		    }//stage

            stage('Install dependent Package on DEV Scratch Org'){
            when { expression { return ((enable_Deploy == "true") && (enable_Install_Dependent_Packages_DEV == "true")) } }
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                         
                            sh """
                                echo "Installing dependent package on : ${DEV_SCRATCH_ORGNAME}"
                                echo "QA SCRATCH_ORG_NAME: ${DEV_SCRATCH_ORGNAME}"
                                echo "PACKAGE_NAME: ${PACKAGE_NAME}"
                                echo "PROJECT_PATH: ${PROJECT_PATH}"
                                echo "INSTALLATION_KEY: ${INSTALLATION_KEY}"
                                echo "MAX_WAIT_TIME_TO_INSTALL_PACKAGE: ${WAIT_TIME}"
                                echo "DEPENDENT_PACKAGES_FILE_PATH= $workspace/${DEPENDENT_PACKAGES_PATH}"
                                make post-installPackagelist PROJECT_PATH="${PROJECT_PATH}" SCRATCH_ORG_NAME="${DEV_SCRATCH_ORGNAME}" INSTALLATION_KEY="${INSTALLATION_KEY}" POST_INSTALLATION_PACKAGE_FILE_PATH=$workspace/${DEPENDENT_PACKAGES_PATH} WAIT_TIME="${WAIT_TIME}"
                            """
                        }                  
                    }//script                    
	            } //steps
                post{
                    failure{
                        sendNotifications("Failure","${teams_NotificationChannel}","Install dependent Package on DEV Scratch Org failed")
                        deleteScratchOrg("${DEV_SCRATCH_ORGNAME}","${HUB_USERNAME}", "${DEV_SCRATCH_USERNAME}" )
                    }
                }  
		    }//stage


  
            stage('Deploy to Dev Scratch org'){
            when { expression { return ( enable_Deploy == "true") } }
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                    
                            sh """
                                echo "deploy and unit test"
                                echo "SCRATCH_ORG_NAME: ${DEV_SCRATCH_ORGNAME}"
                                echo "GIT_BRANCH: ${git_branch}"
                                echo "SFDX_FOLDER_PATH: ${SFDX_FOLDER_PATH}"
                                echo "workspace: $workspace"
                                make deploy-to-ScratchOrg SCRATCH_ORG_NAME="${DEV_SCRATCH_ORGNAME}" PROJECT_PATH=${PROJECT_PATH} SFDX_FOLDER_PATH="${SFDX_FOLDER_PATH}"
                            """
                        }                  
                    }//script                    
	            } //steps
                post{
                    failure{
                        sendNotifications("Failure","${teams_NotificationChannel}","Deploy to Dev Scratch org failed")
                        deleteScratchOrg("${DEV_SCRATCH_ORGNAME}","${HUB_USERNAME}", "${DEV_SCRATCH_USERNAME}" )
                    }
                } 
		    }//stage


            stage('Deploy Additional Component to Scratch org to DEV scratch org'){
            when { expression { return ((enable_Deploy == "true") && (enable_Deploy_Additional_Component_On_DEV == "true")) } }             
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                    
                            sh """
                                echo "deployment started "
                                echo "SCRATCH_ORG_NAME: ${DEV_SCRATCH_ORGNAME}"
                                echo "GIT_BRANCH: ${git_branch}"
                                echo "workspace: $workspace"
                                echo "ADDITIONAL_SRC_PATH: $DEV_ADDITIONAL_SRC_PATH"
                                make deploy_additional_Component_to_Scratch SCRATCH_ORG_NAME="${DEV_SCRATCH_ORGNAME}" ADDITIONAL_SRC_PATH=$workspace/${DEV_ADDITIONAL_SRC_PATH}
                            """
                        }                  
                    }//script                    
	            } //steps
                post{
                    failure{
                        sendNotifications("Failure","${teams_NotificationChannel}","Deploy Additional Component to Scratch org to DEV scratch org failed")
                        deleteScratchOrg("${DEV_SCRATCH_ORGNAME}","${HUB_USERNAME}", "${DEV_SCRATCH_USERNAME}" )
                    }
                } 
		    }//stage


            stage('Execute Apexcode with ToolingAPI'){
            when { expression { return ( enable_execute_code == "true") } }
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                    
                            sh """
                                echo "Executing the given code snippet using ToolingAPI (Automating the code execution in DevConsle"
                                echo "SCRATCH_ORG_NAME: ${DEV_SCRATCH_ORGNAME}"
                                echo "PROJECT_PATH: ${PROJECT_PATH}"
                                echo "POST_INSTALLATION_PACKAGE_FILE_PATH= $workspace/${CODE_SNIPPET_FILE}"
                                make execute-Code-in-devconsole SCRATCH_ORG_NAME="${DEV_SCRATCH_ORGNAME}" PROJECT_PATH="${PROJECT_PATH}" CODE_SNIPPET_FILE_PATH=$workspace/${CODE_SNIPPET_FILE}
                            """
                        }                  
                    }//script                    
	            } //steps
                post{
                    failure{
                        sendNotifications("Failure","${teams_NotificationChannel}","Executing the given code snippet using ToolingAPI is failed")
                    }
                }                 
		    }//stage

            stage('Run Unit Test'){
            when { expression { return ((enable_Deploy == "true") && (enable_Unit_Test == "true")) } }                
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                            sh """
                                echo "running apex test with testlevel: ${TESTLEVEL}"
                                echo "SCRATCH_ORG_NAME: ${DEV_SCRATCH_ORGNAME}"
                                echo "GIT_BRANCH: ${git_branch}"
                                echo "workspace: $workspace"
                                make run-unit-test SCRATCH_ORG_NAME="${DEV_SCRATCH_ORGNAME}" TESTLEVEL="${TESTLEVEL}" PROJECT_PATH=${PROJECT_PATH}
                            """
                        }                  
                    }//script                    
	            } //steps
		    }//stage

            stage('Import Test data to scratch org'){         
                when { expression { return ( enable_Import_Test_Data == "true" ) } }         
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                            sh """
                                echo "importing test data to ${DEV_SCRATCH_ORGNAME}"
                                echo "SCRATCH_ORG_NAME: ${DEV_SCRATCH_ORGNAME}"
                                echo "GIT_BRANCH: ${git_branch}"
                                echo "IMPORT_TEST_DATA_PATH: ${IMPORT_TEST_DATA_PATH}"
                                make import-test-data IMPORT_TEST_DATA_PATH=$workspace/"${IMPORT_TEST_DATA_PATH}" PROJECT_PATH="${PROJECT_PATH}" SCRATCH_ORG_NAME="${DEV_SCRATCH_ORGNAME}"
                            """
                        }                  
                    }//script                    
	            } //steps
/*
                post{
                    failure{
                        sendNotifications("Failure","${teams_NotificationChannel}","Import Test data to scratch org failed")
                        deleteScratchOrg("${DEV_SCRATCH_ORGNAME}","${HUB_USERNAME}", "${DEV_SCRATCH_USERNAME}")
                    }
                } 
*/
		    }//stage

            stage('Run BVT downstream job '){
            when { expression { return ((enable_Deploy == "true") && (params.RUN_BVT_TEST)) } }              
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                         
                            downstreamJob="${BVT_JOB_NAME}"
                            clientid="${CLIENT_ID}"
                            clientsecret="${CLIENT_ID}"

                            scratchOrgPropertieFilePath="./${DEV_SCRATCH_ORG_DETAILS_TEMP_FILE}.properties"
                            scratchDetails = readProperties file: scratchOrgPropertieFilePath
                            
                            scratch_org_user=scratchDetails['username']
                            scratch_org_password=scratchDetails['password']
                            InstanceUrl=scratchDetails['instanceUrl']

                            echo "downstreamJob: ${downstreamJob}"
                            echo "clientid: ${clientid}" 
                            echo "clientsecret: ${clientsecret}"                                                         
                            echo "scratch_org_user: ${scratch_org_user}"
                            echo "scratch_org_user: ${scratch_org_password}"
                            echo "InstanceUrl: ${InstanceUrl}"

                            echo "starting BVT downstream job"
                            runBVT(downstreamJob,clientid,clientsecret,scratch_org_user,scratch_org_password,InstanceUrl)
                        }                  
                    }//script                    
	            } //steps
		    }//stage


// --------------CREATE PACKAGE, PACKAGE VERSION, INSTALL PACKAGE--------------------------

            stage('CreatePackageVersion'){
            when { expression { return (params.ENABLE_PACKAGE_CREATION) } }    
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                    
                            sh """
                                echo "CreatePackage with type: ${PACKAGE_TYPE} and namespace: ${NAMESPACE}"                    
                                echo "GIT_BRANCH: ${git_branch}"
                                echo "PACKAGE_NAME: ${PACKAGE_NAME}"
                                echo "PACKAGE_TYPE: ${PACKAGE_TYPE}"
                                echo "PROJECT_PATH: ${PROJECT_PATH}"
                                echo "NAMESPACE: ${NAMESPACE}"
                                echo "GIT_BRANCH: ${git_branch}"
                                echo "HUB_USERNAME: ${HUB_USERNAME}"
                                echo "API_VERSION: ${API_VERSION}"
                                echo "INSTALLATION_KEY: ${INSTALLATION_KEY}"
                                echo "WAIT_TIME: ${WAIT_TIME}"
                                echo "VERSION_NUMBER: ${VERSION_NUMBER}"
                                echo "SFDX_PROJECT_JSON_PATH: ${SFDX_PROJECT_JSON_PATH}"
                                echo "SFDX_FOLDER_PATH: ${SFDX_FOLDER_PATH}"
                                echo "IS_PROJECT_ALREADY_IN_SFDX_FORMAT: ${IS_PROJECT_ALREADY_IN_SFDX_FORMAT}"
                                make createPackageVersion PACKAGE_NAME="${PACKAGE_NAME}" PACKAGE_TYPE="${PACKAGE_TYPE}" PROJECT_PATH="${PROJECT_PATH}" HUB_USERNAME="${HUB_USERNAME}" NAMESPACE="${NAMESPACE}" INSTALLATION_KEY="${INSTALLATION_KEY}" WAIT_TIME="${WAIT_TIME}" API_VERSION="${API_VERSION}" SCRATCH_DEFINITION_FILE_PATH="$workspace/${DEV_SCRATCH_ORG_DEFINITION_FILE}" VERSION_NUMBER="${VERSION_NUMBER}" SFDX_PROJECT_JSON_PATH="${SFDX_PROJECT_JSON_PATH}" IS_PROJECT_ALREADY_IN_SFDX_FORMAT="${IS_PROJECT_ALREADY_IN_SFDX_FORMAT}" SFDX_FOLDER_PATH="${SFDX_FOLDER_PATH}"
                            """
                        }                  
                    }//script                    
	            } //steps
		    }//stage }
           
            stage('Create QA Scratch Org'){
            when { expression { return (params.CREATE_QA_SCRATCH_ORG) } }
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                    
                            sh """
                                echo "Creating QAScratchOrg for installing package "
                                echo "QA SCRATCH_ORG_NAME: ${QA_SCRATCH_ORGNAME}"
                                echo "QA_SCRATCH_USERNAME: ${QA_SCRATCH_USERNAME}"
                                echo "QA SCRATCH_ORG_DURATION: ${SCRATCH_ORG_VALIDITY_DAYS}"
                                echo "QA ORGNAME: ${QA_SCRATCH_ORGNAME}"
                                echo "NAMESPACE: ${NAMESPACE}"
                                echo "API_VERSION: ${API_VERSION}"                                
                                echo "EDITION: ${QA_EDITION}"
                                echo "PROJECT_PATH: /${PROJECT_PATH}"
                                echo "SCRATCH_DEFINITION_FILE_PATH: $workspace/${QA_SCRATCH_ORG_DEFINITION_FILE}"
                                echo "HUB_USERNAME: ${HUB_USERNAME}"
                                echo "devopsconfig.properties: $workspace/devopsconfig.properties"
                                make create-scratchORG SCRATCH_ORG_NAME="${QA_SCRATCH_ORGNAME}" SCRATCH_ORG_DURATION="${QA_SCRATCH_ORG_VALIDITY_DAYS}" ORGNAME="${QA_SCRATCH_ORGNAME}" EDITION="${QA_EDITION}" PROJECT_PATH=${PROJECT_PATH} SCRATCH_DEFINITION_FILE_PATH=$workspace/${QA_SCRATCH_ORG_DEFINITION_FILE} HUB_USERNAME=${HUB_USERNAME} NAMESPACE="${NAMESPACE}" SCRATCH_ORG_DETAILS_TEMP_FILE=$workspace/"${QA_SCRATCH_ORG_DETAILS_TEMP_FILE}" SCRATCH_USERNAME_TO_CREATE="${QA_SCRATCH_USERNAME}" API_VERSION="${API_VERSION}" ADMIN_EMAIL="${USER_EMAIL}"
                            """
                            // Send QA scratch org details to Team channel
                            scratchOrgPropertieFilePath="./${QA_SCRATCH_ORG_DETAILS_TEMP_FILE}.properties"
                            scratchDetails = readProperties file: scratchOrgPropertieFilePath
                            scratch_org_user=scratchDetails['username']
                            scratch_org_password=scratchDetails['password']
                            instanceUrl=scratchDetails['instanceUrl']
                            scratchorgDetails="INSTANCE_URL: ${instanceUrl} \r\n ${'\n'} SCRATCH_ORG_USER: ${scratch_org_user} \r\n ${'\n'} SCRATCH_ORG_PASSWORD: ${scratch_org_password} \r\n ${'\n'}"
                            echo "${scratchorgDetails}"
                            
                            sendNotifications("INFO","${teams_NotificationChannel}","Authorize DevHub", "QA Scratch Org Details: \r\n ${'\n'} ${scratchorgDetails}")
                        }                  
                    }//script                    
	            } //steps
                post{
                    failure{
                        sendNotifications("Failure","${teams_NotificationChannel}","QA scratch org creation failed")
                    }
                } 
		    }//stage

            stage('Install dependent Package on QA Scratch Org'){
            when { expression { return ((params.CREATE_QA_SCRATCH_ORG) && (enable_Install_Dependent_Packages_QA == "true")) } }
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                         
                            sh """
                                echo "Installing dependent package on : ${QA_SCRATCH_ORGNAME}"
                                echo "QA SCRATCH_ORG_NAME: ${QA_SCRATCH_ORGNAME}"
                                echo "PACKAGE_NAME: ${PACKAGE_NAME}"
                                echo "PROJECT_PATH: ${PROJECT_PATH}"
                                echo "INSTALLATION_KEY: ${INSTALLATION_KEY}"
                                echo "POST_INSTALLATION_PACKAGE_FILE_PATH= $workspace/${DEPENDENT_PACKAGES_PATH}"
                                make post-installPackagelist PROJECT_PATH="${PROJECT_PATH}" SCRATCH_ORG_NAME="${QA_SCRATCH_ORGNAME}" INSTALLATION_KEY="${INSTALLATION_KEY}" POST_INSTALLATION_PACKAGE_FILE_PATH=$workspace/${DEPENDENT_PACKAGES_PATH}
                            """
                        }                  
                    }//script                    
	            } //steps
                post{
                    failure{
                        sendNotifications("Failure","${teams_NotificationChannel}","Install dependent Package on QA Scratch Org failed")
                        deleteScratchOrg("${QA_SCRATCH_ORGNAME}","${HUB_USERNAME}", "${QA_SCRATCH_USERNAME}" )
                    }
                }  
		    }//stage


            stage('Install Created Package on QA Scratch Org'){
            when { expression { return (params.ENABLE_PACKAGE_CREATION) && (params.CREATE_QA_SCRATCH_ORG)} }
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                         
                            sh """
                                echo "Installing package on : ${QA_SCRATCH_ORGNAME}"
                                echo "QA SCRATCH_ORG_NAME: ${QA_SCRATCH_ORGNAME}"
                                echo "PACKAGE_NAME: ${PACKAGE_NAME}"
                                echo "PROJECT_PATH: ${PROJECT_PATH}"
                                echo "INSTALLATION_KEY: ${INSTALLATION_KEY}"
                                make installPackage PACKAGE_NAME="${PACKAGE_NAME}" PROJECT_PATH=${PROJECT_PATH} SCRATCH_ORG_NAME="${QA_SCRATCH_ORGNAME}" INSTALLATION_KEY=${INSTALLATION_KEY} HUB_USERNAME=${HUB_USERNAME}
                            """
                        }                  
                    }//script                    
	            } //steps
                post{
                    failure{
                        sendNotifications("Failure","${teams_NotificationChannel}","Install Package on QA Scratch Org failed")
                        deleteScratchOrg("${QA_SCRATCH_ORGNAME}","${HUB_USERNAME}", "${QA_SCRATCH_USERNAME}" )
                    }
                }  
		    }//stage

            stage('Deploy Additional Component to QA Scratch org'){
            when { expression { return ((params.CREATE_QA_SCRATCH_ORG) && (enable_Deploy_Additional_Component_On_QA == "true")) } }                
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                    
                            sh """
                                echo "deployment started "
                                echo "SCRATCH_ORG_NAME: ${QA_SCRATCH_ORGNAME}"
                                echo "GIT_BRANCH: ${git_branch}"
                                echo "workspace: $workspace"
                                echo "ADDITIONAL_SRC_PATH: ${QA_ADDITIONAL_SRC_PATH}"
                                make deploy_additional_Component_to_Scratch SCRATCH_ORG_NAME=${QA_SCRATCH_ORGNAME} ADDITIONAL_SRC_PATH=$workspace/${QA_ADDITIONAL_SRC_PATH}
                            """
                        }                  
                    }//script                    
	            } //steps
                post{
                    failure{
                        sendNotifications("Failure","${teams_NotificationChannel}","Deploy Additional Component to QA Scratch org failed")
                        deleteScratchOrg("${QA_SCRATCH_ORGNAME}","${HUB_USERNAME}", "${QA_SCRATCH_USERNAME}" )
                    }
                }  
		    }//stage

            stage('Install Post dependent Packages on QA Scratch Org'){
            when { expression { return ((params.CREATE_QA_SCRATCH_ORG) && (enable_Post_Install_Dependent_Packages_QA == "true")) } }
                steps {
	                script{
	                    container('ic-sfdx-builder'){
                         
                            sh """
                                echo "Installing dependent package on : ${QA_SCRATCH_ORGNAME}"
                                echo "QA SCRATCH_ORG_NAME: ${QA_SCRATCH_ORGNAME}"
                                echo "PACKAGE_NAME: ${PACKAGE_NAME}"
                                echo "PROJECT_PATH: ${PROJECT_PATH}"
                                echo "INSTALLATION_KEY: ${INSTALLATION_KEY}"
                                echo "POST_INSTALLATION_PACKAGE_FILE_PATH= $workspace/${POST_DEPENDENT_PACKAGES_PATH}"
                                make post-installPackagelist PROJECT_PATH="${PROJECT_PATH}" SCRATCH_ORG_NAME="${QA_SCRATCH_ORGNAME}" INSTALLATION_KEY="${INSTALLATION_KEY}" POST_INSTALLATION_PACKAGE_FILE_PATH=$workspace/${DEPENDENT_PACKAGES_PATH}
                            """
                        }                  
                    }//script                    
	            } //steps
                post{
                    failure{
                        sendNotifications("Failure","${teams_NotificationChannel}","Install dependent Package on QA Scratch Org failed")
                        deleteScratchOrg("${QA_SCRATCH_ORGNAME}","${HUB_USERNAME}", "${QA_SCRATCH_USERNAME}" )
                    }
                }  
		    }//stage
   
		}// stages
	     
    }// Pipeline
} // call body
    
void runBVT(String downstreamJob, String clientid, String clientsecret, String scratch_org_user, String scratch_org_password, String InstanceUrl) {
    try{
        def jobResult = build(
                                job: "${downstreamJob}", 
                                parameters: [

                                    string(name: 'LoginUser', value: "${scratch_org_user}"),
                                    string(name: 'LoginPassword', value: "${scratch_org_password}"),
                                    string(name: 'clientid', value: "${clientid}"),
                                    string(name: 'clientsecret', value: "${clientsecret}"),
                                    string(name: 'InstanceUrl', value: "${InstanceUrl}")
                                ],
                                wait: true
                            )
        
    } catch (error){
        sendNotifications("Failure","testnotification","cicd", "Failed to call the downstream job to clean up the old artifacts.!!")
    }
}

void deleteScratchOrg(String scratchOrg, String devHubUser, String scratchOrgUser){
    try{
        container('ic-sfdx-builder'){                       
            sh """
                echo "Deleting Scratch Org"                    
                echo "SCRATCH_ORG_NAME: ${scratchOrg}"
                echo "HUB_USERNAME: ${devHubUser}"
                make delete-ScratchOrg SCRATCH_ORG_NAME="${scratchOrg}" HUB_USERNAME="${devHubUser}"
            """
            scratchorgDetails="SCRATCH_ORG_USER: ${scratchOrgUser} \r\n ${'\n'} SCRATCH_ORG_NAME: ${scratchOrg} \r\n ${'\n'}"
            sendNotifications("INFO","${teams_NotificationChannel}","Delete ScratchOrg", "Scratch Org deleted: \r\n ${'\n'} ${scratchorgDetails}", )
        }
    } catch(error){
          sendNotifications("Failure","${teams_NotificationChannel}","CICD", "Failed to delete Scratch org: ${scratchOrg}")
          throw error
    }// catch     
}


void sendNotifications(String buildStatus = 'INFO', String channel, String stageName, String additinalMsg = "" ) {
  def colorName = 'RED'
  def colorCode = '#FF0000'
  def subject = "${buildStatus}: Job '${env.JOB_NAME} [${currentBuild.displayName}]' ${'\n'}"
  def summary = """${subject} \r\n Job Name: '${env.JOB_NAME}' \r\n ${'\n'} Build Number: [${currentBuild.displayName}] \r\n ${'\n'} Build URL: (${env.BUILD_URL}) \r\n ${'\n'} Stage Name: ${stageName}  
  Additional Info: \r\n ${'\n'} \r\n ${'\n'} ${additinalMsg} """
  def details = """<p>${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
    <p>Check console output at &QUOT;<a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>&QUOT;</p>"""
  if (buildStatus == 'WARN') {
    color = 'ORANGE'
    colorCode = '#FF4000'
  } else if (buildStatus == 'DEBUG') {
    color = 'GREY'
    colorCode = '#848484'
  } 
  else if (buildStatus == 'INFO') {
    color = 'GREEN'
    colorCode = '#00FF00'
  }
  else if (buildStatus == 'Failure') {
    color = 'RED'
    colorCode = '#FF0000'
  } else {
    color = 'BLACK'
    colorCode = '#000000'
  }

  
/*
  hipchatSend (color: color, notify: true, message: summary)

  emailext (
      to: 'bitwiseman@bitwiseman.com',
      subject: subject,
      body: details,
      recipientProviders: [[$class: 'DevelopersRecipientProvider']]
    )
*/

//Send notification on MSFT Teams 
//Default channel is cicd-alert group
  webhookChannel="https://outlook.office.com/webhook/85806430-022c-47e1-8176-89f479fb2cbd@3a41ae53-fb35-4431-be7b-a0b3e1aee3c0/JenkinsCI/29fef30a618c47c7ad729bd0f9937c65/b97b5136-db72-4aad-a314-e7d4a765a878"
  
  if(channel.contains("https://outlook.office.com/webhook")){
    webhookChannel=channel
  } else {
    //Notify on Slack
    slackSend (color: colorCode,channel: channel, message: summary)
  }
  //Notify on MSFT TEAMS
  office365ConnectorSend message: summary,status:buildStatus,webhookUrl:webhookChannel,color: colorCode 
}

/**
  * Generates a path to a temporary file location, ending with {@code path} parameter.
  * 
  * @param path path suffix
  * @return path to file inside a temp directory
  */
@NonCPS
String createTempLocation(String jenkinsFilePath, String path) {
  String tmpDir = pwd tmp: true
  def workspace = env.WORKSPACE + File.separator + jenkinsFilePath
  return workspace + File.separator + new File(path).getName()
}

/**
  * Returns the path to a temp location of a script from the global library (resources/ subdirectory)
  *
  * @param srcPath path within the resources/ subdirectory of this repo
  * @param destPath destination path (optional)
  * @return path to local file
  */
String copyGlobalLibraryScript(String jenkinsFilePath, String srcPath, String destPath = null) {
  destPath = destPath ?: createTempLocation(jenkinsFilePath, srcPath)
  writeFile file: destPath, text: libraryResource(srcPath)
  echo "copyGlobalLibraryScript: copied ${srcPath} to ${destPath}"
  return destPath
}

