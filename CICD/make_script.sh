set -eo pipefail
#------------------------------------------------------------------#
# Function Name: LOG_INFO
# Description: This method is used for printing INFO logs
#------------------------------------------------------------------#
function LOG_INFO()
{
    LOG_MESSAGE_LINE="`date +%Y-%m-%d-%H:%M:%S` [INFO]  $1"
    echo -e  $LOG_MESSAGE_LINE
}

function LOG_ERROR()
{
    LOG_MESSAGE_LINE="`date +%Y-%m-%d-%H:%M:%S` [ERROR] $1 "
    echo -e $LOG_MESSAGE_LINE
    exit 1
}

function authenticate_DevHub()
{
    CONSUMER_KEY=$1
    HUB_USERNAME=$2
    JWT_KEY_FILE=$3
    LOG_INFO "Authenticate to the Dev Hub using the server key"
    sfdx force:auth:jwt:grant --clientid ${CONSUMER_KEY} --username ${HUB_USERNAME} --jwtkeyfile ${JWT_KEY_FILE} --setdefaultdevhubusername
    if [ $? -eq 0 ]; then
       LOG_INFO "Authenticated DevHub account"
    else
       LOG_INFO "Authentication to DevHub account failed"
    fi
    LOG_INFO "Org list"
    sfdx force:org:list

   # LOG_INFO "Create org limit"
   # sfdx force:limits:api:display -u ${HUB_USERNAME}
}

function create_Project()
{
    PROJECT_NAME=$1
    WORKSPACE=$2
    LOG_INFO "Creating SFDX project: $PROJECT_NAME in $WORKSPACE"
    cd ${WORKSPACE}
    sfdx force:project:create -n $PROJECT_NAME
    LOG_INFO "Removing force-app folder"
    cd $PROJECT_NAME
    rm -rf force-app
}


function create_scratchORG()
{
    SCRATCH_ORG_NAME=$1
    SCRATCH_ORG_DURATION=$2
    ORGNAME=$3
    EDITION=$4
    PROJECT_PATH=$5
    SCRATCH_DEFINITION_FILE_PATH=$6
    HUB_USERNAME=$7
    NAMESPACE=$8
    SCRATCH_ORG_DETAILS_TEMP_FILE=$9
    SCRATCH_USERNAME_TO_CREATE=${10}
    API_VERSION=${11}
    ADMIN_EMAIL=${12}

    # cd to SFDX project
    cd $PROJECT_PATH

    LOG_INFO "Create scratch org: ${SCRATCH_ORG_NAME}"

    if [[ -n "${NAMESPACE}"  && -n "${API_VERSION}" ]]; then
        LOG_INFO "update_sfdx-project-json-file: projectpath: ${PROJECT_PATH} with api version: ${API_VERSION} and namespace: ${NAMESPACE}"
        update_sfdx-project-json-file ${PROJECT_PATH} "${NAMESPACE}" ${API_VERSION}
    fi 

     if [[ -z "${NAMESPACE}"  && -n "${API_VERSION}" ]]; then
        LOG_INFO "update_sfdx-project-json-file: projectpath: ${PROJECT_PATH} with api version: ${API_VERSION} and namespace: ${NAMESPACE}"
        update_sfdx-project-json-file ${PROJECT_PATH} "${NAMESPACE}" ${API_VERSION}
    fi 
    
    if [[ -n "${ORGNAME}" && -n "${EDITION}" ]]; then
        LOG_INFO "Creating scratchOrg ${SCRATCH_ORG_NAME} using ${SCRATCH_DEFINITION_FILE_PATH} file"
        cat "${SCRATCH_DEFINITION_FILE_PATH}"
        sfdx force:org:create -a "${SCRATCH_ORG_NAME}" orgName="${ORGNAME}" edition="${EDITION}" -f "${SCRATCH_DEFINITION_FILE_PATH}" username="${SCRATCH_USERNAME_TO_CREATE}" adminEmail=${ADMIN_EMAIL} --durationdays=${SCRATCH_ORG_DURATION} --targetdevhubusername ${HUB_USERNAME}
    else  
        LOG_INFO "Creating scratchOrg ${SCRATCH_ORG_NAME} using ${SCRATCH_DEFINITION_FILE_PATH} file"
        cat "${SCRATCH_DEFINITION_FILE_PATH}"
        sfdx force:org:create -a "${SCRATCH_ORG_NAME}" -f "${SCRATCH_DEFINITION_FILE_PATH}" username="${SCRATCH_USERNAME_TO_CREATE}" --durationdays="${SCRATCH_ORG_DURATION}" adminEmail=${ADMIN_EMAIL} --targetdevhubusername ${HUB_USERNAME}
    fi

    LOG_INFO "generate password"
    sfdx force:user:password:generate -u "${SCRATCH_ORG_NAME}" --targetdevhubusername "${HUB_USERNAME}"
    
    LOG_INFO "display org details"
    sfdx force:user:display -u "${SCRATCH_ORG_NAME}" --targetdevhubusername "${HUB_USERNAME}"
    update_Scratch_Details "${SCRATCH_ORG_NAME}" "${SCRATCH_ORG_DETAILS_TEMP_FILE}" "${HUB_USERNAME}"

    LOG_INFO "display org list"
    sfdx force:org:list 

    ORG_INFO=$(sfdx force:org:display -u "${SCRATCH_ORG_NAME}" --json)
    ORG_DOMAIN=$(echo $ORG_INFO | jq .result.instanceUrl -r)

    LOG_INFO "result org info: ${ORG_DOMAIN} "
}


function execute_Code_in_devconsole()
{
    SCRATCH_ORG_NAME=$1
    PROJECT_PATH=$2
    CODE_SNIPPET_FILE_PATH=$3

    cd $PROJECT_PATH    
    sfdx force:org:display -u "${SCRATCH_ORG_NAME}" --json
    ORG_INFO=$(sfdx force:org:display -u "${SCRATCH_ORG_NAME}" --json)
    ORG_DOMAIN=$(echo $ORG_INFO | jq .result.instanceUrl -r)
    ORG_ACCESSTOKEN=$(echo $ORG_INFO | jq .result.accessToken -r)
    LOG_INFO $ORG_DOMAIN
    LOG_INFO $ORG_ACCESSTOKEN
 
    while IFS== read -r code_number code ; do
        code=${code%\"}; code=${code#\"}; key=${code_number#export };
        LOG_INFO "Executing: $code_number with snippet: ${code} using ToolingAPI on ${SCRATCH_ORG_NAME}"
	      curl -s  -X GET -H "Authorization: Bearer $ORG_ACCESSTOKEN" -G --data-urlencode "anonymousBody=$code" $ORG_DOMAIN/services/data/v48.0/tooling/executeAnonymous/ | jq '.' > test.json 
	      cat test.json
        LOG_INFO "Success status of ${code_number} --- $(cat test.json | jq '.success')"
      

	    if [ "$(cat test.json | jq '.compiled')" = true  ]; then
          if [ "$(cat test.json | jq '.success')" = true ]; then
               LOG_INFO "Executed successfully in the created scratchOrg"
	        else
	             LOG_ERROR "Code is Compiled successfully, But failed to execute $code_number with snippet: ${code}"
               LOG_ERROR "Exception message when executing the $code_number is $(cat test.json | jq '.exceptionMessage')"
          fi
      else
        LOG_ERROR "Code failed at Compilation with comiple problem - $(cat test.json | jq '.compileProblem')"
	    fi
    done < $CODE_SNIPPET_FILE_PATH
   
   
}


function update_Scratch_Details()
{
    SCRATCH_ORG_NAME=$1
    SCRATCH_ORG_DETAILS_TEMP_FILE=$2   
    HUB_USERNAME=$3
    
    LOG_INFO "updating "${SCRATCH_ORG_DETAILS_TEMP_FILE}" with ${SCRATCH_ORG_NAME} details"
   # sfdx force:user:display -u ${SCRATCH_ORG_NAME} --json | jq .result -r | sed 's/[{},"^ *]//g' | sed 's/[:]/=/g' >> ${SCRATCH_ORG_DETAILS_TEMP_FILE}
    sfdx force:user:display -u "${SCRATCH_ORG_NAME}" --targetdevhubusername "${HUB_USERNAME}" --json | jq .result -r |  sed 's/[," ]//g' | sed '1d;$d' >> ${SCRATCH_ORG_DETAILS_TEMP_FILE}.properties
    LOG_INFO "added "${SCRATCH_ORG_NAME}" details in ${SCRATCH_ORG_DETAILS_TEMP_FILE}.properties "
    cat ${SCRATCH_ORG_DETAILS_TEMP_FILE}.properties
}

function deploy_to_Scratch()
{
    SCRATCH_ORG_NAME=$1
    PROJECT_PATH=$2
    SFDX_FOLDER_PATH=$3
    cd ${PROJECT_PATH}
    ls -lart 
    ls -lart ${PROJECT_PATH}/"${SFDX_FOLDER_PATH}"/main/default
    LOG_INFO "deploying $SRC_PATH to scratchORG: ${SCRATCH_ORG_NAME} "
    sfdx force:source:push -u "${SCRATCH_ORG_NAME}"
}
function deploy_additional_Component_to_Scratch()
{
    SCRATCH_ORG_NAME=$1
    ADDITIONAL_SRC_PATH=$2
    LOG_INFO "deploying $ADDITIONAL_SRC_PATH to scratchORG: ${SCRATCH_ORG_NAME}"
    sfdx force:mdapi:deploy -d ${ADDITIONAL_SRC_PATH}/ -u "${SCRATCH_ORG_NAME}" -w 15
}


function run_apex_test()
{
    SCRATCH_ORG_NAME=$1
    TESTLEVEL=$2
    PROJECT_PATH=$3
    LOG_INFO "running test on scratchORG: ${SCRATCH_ORG_NAME}"
    cd $PROJECT_PATH
    TEST_RUN_ID=$(sfdx force:apex:test:run -l "${TESTLEVEL}" -u "${SCRATCH_ORG_NAME}" --json | jq .result.testRunId -r)
    LOG_INFO "running testreport with RUN ID: $TEST_RUN_ID"
    sfdx force:apex:test:report -i "${TEST_RUN_ID}" -u "${SCRATCH_ORG_NAME}" --wait 30 
}

function import_test_data()
{
    IMPORT_TEST_DATA_PATH=$1
    PROJECT_PATH=$2
    SCRATCH_ORG_NAME=$3
    cd ${PROJECT_PATH}
 
    LOG_INFO "Start importing data to scratch org ${SCRATCH_ORG_NAME}"
    sfdx force:data:tree:import -p ${IMPORT_TEST_DATA_PATH} -u ${SCRATCH_ORG_NAME}
}

function convert_to_SfdxFormat()
{
    PROJECT_PATH=$1
    SRC_PATH=$2
    LOG_INFO "SFDX project path: $PROJECT_PATH"
    LOG_INFO "SRC path: SRC_PATH"
    LOG_INFO "Convert the metadata code into a SFDX format the scratch org can understand"
    cd $PROJECT_PATH
    sfdx force:mdapi:convert -r $SRC_PATH 
}

function createPackageVersion()
{
    PACKAGE_NAME=$1
    PACKAGE_TYPE=$2
    PROJECT_PATH=$3
    HUB_USERNAME=$4
    NAMESPACE=$5
    INSTALLATION_KEY=$6
    WAIT_TIME=$7
    API_VERSION=$8
    SCRATCH_DEFINITION_FILE_PATH=$9
    VERSION_NUMBER=${10}
    SFDX_PROJECT_JSON_PATH=${11}
    IS_PROJECT_ALREADY_IN_SFDX_FORMAT=${12}
    SFDX_FOLDER_PATH=${13}

    cd $PROJECT_PATH

    if [ "${IS_PROJECT_ALREADY_IN_SFDX_FORMAT}" = false  ]; then
        LOG_INFO " remove existing sfdx-project.json in ${PROJECT_PATH}"
        rm -rf sfdx-project.json
        cp ${SFDX_PROJECT_JSON_PATH} ${PROJECT_PATH}
    fi
    # else use sfdx-project.json defined in project
    LOG_INFO "Check if package :${PACKAGE_NAME} already exist"
    LOG_INFO "update_sfdx-project-json-file: projectpath: ${PROJECT_PATH},api_version: "${API_VERSION}"  namespace: ${NAMESPACE}"
    update_sfdx-project-json-file ${PROJECT_PATH} "${NAMESPACE}" ${API_VERSION}
    PACKAGE_Name_JSON=$(eval sfdx force:package:list -v ${HUB_USERNAME} --json | jq '.result' | jq '.[] | select(.Name == "'"${PACKAGE_NAME}"'")' | jq '. | select(.NamespacePrefix == "'"${NAMESPACE}"'")')
    #PACKAGE_VERSION_JSON=$(eval sfdx force:package:version:list -v ${HUB_USERNAME} --json | jq '.result | sort_by(.MajorVersion, .MinorVersion, .PatchVersion, .BuildNumber)' | jq '.[0] | select(.Package2Name == "'${PACKAGE_NAME}'")')
    Package_Name=$(jq -r '.Name' <<< $PACKAGE_Name_JSON)
    echo "$Package_Name"
    if [ -z "${Package_Name}" ]; then
        LOG_INFO "No existing package with name: ${PACKAGE_NAME} found in Devhub"
        if [ -z "${NAMESPACE}" ]; then
           LOG_INFO "Creating new package ${PACKAGE_NAME} with NO NAMESPACE"
           sfdx force:package:create --name "${PACKAGE_NAME}" --packagetype ${PACKAGE_TYPE} --path "${SFDX_FOLDER_PATH}" --nonamespace --targetdevhubusername ${HUB_USERNAME}   
        else
           LOG_INFO "Creating new package ${PACKAGE_NAME} with NAMESPACE ${NAMESPACE}"
           sfdx force:package:create --name "${PACKAGE_NAME}" --packagetype ${PACKAGE_TYPE} --path "${SFDX_FOLDER_PATH}" --targetdevhubusername ${HUB_USERNAME}   
        fi
        cat ${PROJECT_PATH}/sfdx-project.json
        LOG_INFO "package created"
        LOG_INFO "Creating Package Version which will be fixed snapshot of the package contents and related metadata"

        if [ -n "${VERSION_NUMBER}" ]; then
           LOG_INFO "Package Version: ${VERSION_NUMBER}"
           sfdx force:package:version:create -p "${PACKAGE_NAME}"  -k ${INSTALLATION_KEY} --versionnumber "${VERSION_NUMBER}" --wait ${WAIT_TIME} -v ${HUB_USERNAME} -f ${SCRATCH_DEFINITION_FILE_PATH} 
           cat ${PROJECT_PATH}/sfdx-project.json 
           fetch_SubscriberPackageVersionId "${PACKAGE_NAME}" ${VERSION_NUMBER} ${PROJECT_PATH} ${NAMESPACE}
        else 
           LOG_INFO "Using Package Version from default sfdx-project.json"
           sfdx force:package:version:create -p "${PACKAGE_NAME}" -k ${INSTALLATION_KEY} --wait "${WAIT_TIME}" -v ${HUB_USERNAME} -f ${SCRATCH_DEFINITION_FILE_PATH} 
           cat ${PROJECT_PATH}/sfdx-project.json
           PACKAGE_VERSION_ID=$(sfdx force:package:version:list -p "${PACKAGE_NAME}" --targetdevhubusername ${HUB_USERNAME} --json | jq .result[0] | jq .SubscriberPackageVersionId -r)
           LOG_INFO "SubscriberPackageVersionId : ${PACKAGE_VERSION_ID}"
           echo "${PACKAGE_VERSION_ID}" > "${PACKAGE_NAME}".TXT           
        fi 
    fi
    if [ "${PACKAGE_NAME}" == "${Package_Name}" ]; then 
       LOG_INFO "Package found :${PACKAGE_NAME}"
       # If user specfied Version Number in Jenkin parameter
       if [[ -n "${VERSION_NUMBER}" ]]; then      
                
                sfdx force:package:version:list -v ${HUB_USERNAME} --json > ${VERSION_NUMBER}.json
                if [ -n "${NAMESPACE}" ]; then
                    PACKAGE_VERSION_ID=$(cat ${VERSION_NUMBER}.json | jq ' .result[]| select((.Version == "'"${VERSION_NUMBER}"'") and select(.Package2Name == "'"${PACKAGE_NAME}"'") and select(.NamespacePrefix=="'"${NAMESPACE}"'"))' | jq .SubscriberPackageVersionId -r )
                else
                    PACKAGE_VERSION_ID=$(cat ${VERSION_NUMBER}.json | jq ' .result[]| select((.Version == "'"${VERSION_NUMBER}"'") and select(.Package2Name == "'"${PACKAGE_NAME}"'") and select(.NamespacePrefix==null))' | jq .SubscriberPackageVersionId -r )
                fi
                if [ -n "${PACKAGE_VERSION_ID}" ]; then
                    LOG_INFO "Given ${VERSION_NUMBER} is already created, please use different version number to create the package"
                    LOG_INFO "SubscriberPackageVersionId : ${PACKAGE_VERSION_ID} and Namespace: ${NAMESPACE}"
                    exit 1
                else
                    LOG_INFO "${VERSION_NUMBER} version is avaialbe to create"
                fi

          LOG_INFO "$VERSION_NUMBER is not existed, Creating $VERSION_NUMBER"
          LOG_INFO "upgrade User specified package version to ${VERSION_NUMBER}"
          sfdx force:package:version:create -p "${PACKAGE_NAME}"  -k ${INSTALLATION_KEY} --versionnumber ${VERSION_NUMBER} --wait "${WAIT_TIME}" -v ${HUB_USERNAME} -f ${SCRATCH_DEFINITION_FILE_PATH} 
        #  sfdx force:package:version:create -p "${PACKAGE_NAME}" --apiversion "${API_VERSION}" -d force-app -k ${INSTALLATION_KEY} --versionnumber $VERSION_NUMBER --wait ${WAIT_TIME} -v ${HUB_USERNAME} -f ${SCRATCH_DEFINITION_FILE_PATH}
          cat ${PROJECT_PATH}/sfdx-project.json
          fetch_SubscriberPackageVersionId "${PACKAGE_NAME}" ${VERSION_NUMBER} ${PROJECT_PATH} ${NAMESPACE}
       else  # autoincrement version number from last package version
          LOG_INFO "Fetching last Major, Minor,Patch from ${PACKAGE_NAME} in DevHub"
  #       PACKAGE_VERSION_JSON=$(eval sfdx force:package:version:list -v ${HUB_USERNAME} --json | jq '.result | sort_by(.MajorVersion, .MinorVersion, .PatchVersion, .BuildNumber)' | jq '[.[] | select(.Package2Name == "'"${PACKAGE_NAME}"'")][0]')
          sfdx force:package:version:list -v ${HUB_USERNAME} --json > "${PACKAGE_NAME}".json
          PACKAGE_VERSION_JSON=$(cat "${PACKAGE_NAME}".json | jq '.result | sort_by(-.MajorVersion, -.MinorVersion, -.PatchVersion, -.BuildNumber)' | jq '[.[] | select(.Package2Name == "'"${PACKAGE_NAME}"'")][0]')
          # No previous package version info                        
          LOG_INFO "Previously installed ${PACKAGE_NAME} Package info: ${PACKAGE_VERSION_JSON}"
          MAJOR_VERSION=$(jq -r '.MajorVersion' <<< $PACKAGE_VERSION_JSON)
          MINOR_VERSION=$(jq -r '.MinorVersion' <<< $PACKAGE_VERSION_JSON)
          PATCH_VERSION=$(jq -r '.PatchVersion' <<< $PACKAGE_VERSION_JSON)
          BUILD_VERSION=$(jq -r '.BuildNumber' <<< $PACKAGE_VERSION_JSON)
          LOG_INFO "Last ${PACKAGE_NAME} Version: ${MAJOR_VERSION}.${MINOR_VERSION}.${PATCH_VERSION}.${BUILD_VERSION} "
          #NEXT_BUILD_VERSION="NEXT"
          NEXT_BUILD_VERSION=$((BUILD_VERSION+1));
          NEW_VERSION_NUMBER="${MAJOR_VERSION}.${MINOR_VERSION}.${PATCH_VERSION}.${NEXT_BUILD_VERSION}"
          echo ${NEW_VERSION_NUMBER}       
          LOG_INFO "upgrading/autoincrement package version to ${NEW_VERSION_NUMBER}"
          #  PACKAGE_VERSION_ID=$(eval sfdx force:package:version:create -p "'"${PACKAGE_NAME}"'" -k ${INSTALLATION_KEY} --versionnumber ${NEW_VERSION_NUMBER} --wait "${WAIT_TIME}" -v ${HUB_USERNAME} -f ${SCRATCH_DEFINITION_FILE_PATH} --json | jq -r '.result.SubscriberPackageVersionId') 
          sfdx force:package:version:create -p "${PACKAGE_NAME}" -k ${INSTALLATION_KEY} --versionnumber ${NEW_VERSION_NUMBER} --wait "${WAIT_TIME}" -v ${HUB_USERNAME} -f ${SCRATCH_DEFINITION_FILE_PATH} 
          cat ${PROJECT_PATH}/sfdx-project.json
          fetch_SubscriberPackageVersionId "${PACKAGE_NAME}" ${NEW_VERSION_NUMBER} ${PROJECT_PATH} ${NAMESPACE}       
       fi 
    fi
}

function installPackage()
{
    PACKAGE_NAME=$1
    PROJECT_PATH=$2
    SCRATCH_ORG_NAME=$3
    INSTALLATION_KEY=$4
    HUB_USERNAME=$5
    LOG_INFO "Install package on ${SCRATCH_ORG_NAME}"
    cd $PROJECT_PATH
    LOG_INFO "List of installed package version "
    #sfdx force:package:install --wait 10 --publishwait 10 --package CLM_Unlocked@0.1.0-1 -k test1234 -r -u $SCRATCH_ORG_NAME
    sfdx force:package:version:list -p "${PACKAGE_NAME}" --targetdevhubusername ${HUB_USERNAME}
    PACKAGE_VERSION_ID=$(cat ./"${PACKAGE_NAME}".TXT) 
    
    #PACKAGE_VERSION_ID=$(sfdx force:package:version:list -p "${PACKAGE_NAME}" --targetdevhubusername ${HUB_USERNAME} --json | jq .result[0] | jq .SubscriberPackageVersionId -r)
    LOG_INFO "SubscriberPackageVersionId: ${PACKAGE_VERSION_ID}"
  
        PACKAGE_RUN_ID=$(sfdx force:package:install --wait 10 --publishwait 10 --package ${PACKAGE_VERSION_ID} -k ${INSTALLATION_KEY} -r -u ${SCRATCH_ORG_NAME}  --json | jq -r .result.Id)
        LOG_INFO "PACKAGE_RUN_ID: ${PACKAGE_RUN_ID}"
        LOG_INFO "Checking if $package_name is installed on ${SCRATCH_ORG_NAME}"
        WAIT_PACKAGE=true
        POLL_LIMIT=30
        POLL_COUNT=0
        while [ ${WAIT_PACKAGE} ] && [ ${POLL_COUNT} -le ${POLL_LIMIT} ];
        do
            LOG_INFO "WAIT_PACKAGE: ${WAIT_PACKAGE}"
            LOG_INFO "POLL_COUNT: ${POLL_COUNT}"
            PACKAGE_STATUS=null
            PACKAGE_STATUS=$(sfdx force:package:install:report -i ${PACKAGE_RUN_ID} -u ${SCRATCH_ORG_NAME} --json | jq -r .result.Status)
            LOG_INFO "PACKAGE_STATUS: ${PACKAGE_STATUS}"
            EXPECTED_STATUS="SUCCESS"
            LOG_INFO "EXPECTED_STATUS: ${EXPECTED_STATUS}"
            if [[ ${PACKAGE_STATUS} == ${EXPECTED_STATUS} ]]; then
                WAIT_PACKAGE=false
                LOG_INFO "Verification of $package_name installed on ${SCRATCH_ORG_NAME} is completed"
                break
            else
                LOG_INFO "PACKAGE_STATUS is: ${PACKAGE_STATUS}"
                sleep 60
            fi
            POLL_COUNT=$(( POLL_COUNT+1 ))
        done

   # LOG_INFO "Below package installed on ${SCRATCH_ORG_NAME}"
   # sfdx force:package:installed:list -u ${SCRATCH_ORG_NAME}
    LOG_INFO "display ${SCRATCH_ORG_NAME} details"
    sfdx force:user:display -u "${SCRATCH_ORG_NAME}" --targetdevhubusername ${HUB_USERNAME}
}

function post_installPackageList()
{
    PROJECT_PATH=$1
    SCRATCH_ORG_NAME=$2
    INSTALLATION_KEY=$3
    POST_INSTALLATION_PACKAGE_FILE_PATH=$4
    LOG_INFO "Installing package on ${SCRATCH_ORG_NAME}"
    cd $PROJECT_PATH    
  
    LOG_INFO "These dependent packages will get installed on ${SCRATCH_ORG_NAME}"
    cat ${POST_INSTALLATION_PACKAGE_FILE_PATH}
 
    while IFS== read -r package_name version_id ; do
        version_id=${version_id%\"}; version_id=${version_id#\"}; key=${package_name#export };
        LOG_INFO "installing package: $package_name with version id: ${version_id} on ${SCRATCH_ORG_NAME}"
        # wait for 10 mins. If not completed in 10 mins start polling
        PACKAGE_RUN_ID=$(sfdx force:package:install --wait 10 --publishwait 10 --package ${version_id} -k ${INSTALLATION_KEY} -r -u "${SCRATCH_ORG_NAME}" --json | jq -r .result.Id)
        LOG_INFO "PACKAGE_RUN_ID: ${PACKAGE_RUN_ID}"
        LOG_INFO "Checking if $package_name is installed on ${SCRATCH_ORG_NAME}"
        WAIT_PACKAGE=true
        POLL_LIMIT=30
        POLL_COUNT=0
        while [ ${WAIT_PACKAGE} ] && [ ${POLL_COUNT} -le ${POLL_LIMIT} ];
        do
            LOG_INFO "WAIT_PACKAGE: ${WAIT_PACKAGE}"
            LOG_INFO "POLL_COUNT: ${POLL_COUNT}"
            PACKAGE_STATUS=null
            PACKAGE_STATUS=$(sfdx force:package:install:report -i ${PACKAGE_RUN_ID} -u ${SCRATCH_ORG_NAME} --json | jq -r .result.Status)
            LOG_INFO "PACKAGE_STATUS: ${PACKAGE_STATUS}"
            EXPECTED_STATUS="SUCCESS"
            LOG_INFO "EXPECTED_STATUS: ${EXPECTED_STATUS}"
            if [[ ${PACKAGE_STATUS} == ${EXPECTED_STATUS} ]]; then
                WAIT_PACKAGE=false
                LOG_INFO "Verification of $package_name installed on ${SCRATCH_ORG_NAME} is completed"
                break
            else
                LOG_INFO "PACKAGE_STATUS is: ${PACKAGE_STATUS}"
                sleep 60
            fi
            POLL_COUNT=$(( POLL_COUNT+1 ))
        done
    done < $POST_INSTALLATION_PACKAGE_FILE_PATH
    LOG_INFO "These are the list of packages installed on ${SCRATCH_ORG_NAME}"
    #sfdx force:package:installed:list -u ${SCRATCH_ORG_NAME} 

   # sfdx force:package:install --wait 10 --publishwait 10 --package 04t0g000000pCeuAAE -k installapttus -r -u buildScratchOrg
}

function update_sfdx-project-json-file()
{
    SFDX_PROJECT_PATH=$1
    NAMESPACE=$2
    API_VERSION=$3

    SFDX_JSON_FILE_PATH=$SFDX_PROJECT_PATH/sfdx-project.json
    LOG_INFO "updating namespace in $SFDX_JSON_FILE_PATH file with namespace: $NAMESPACE"
    LOG_INFO "updating sourceApiVersion in $SFDX_JSON_FILE_PATH file with sourceApiVersion: $API_VERSION"
    tempfile1=$(mktemp -u)
    tempfile2=$(mktemp -u)
    jq ".namespace = "\"$NAMESPACE"\"" $SFDX_JSON_FILE_PATH > $tempfile1
    jq ".sourceApiVersion = "\"$API_VERSION"\"" $tempfile1 > $tempfile2
    mv $tempfile2 $SFDX_JSON_FILE_PATH
    LOG_INFO "$SFDX_JSON_FILE_PATH file updated:"
    cat $SFDX_JSON_FILE_PATH
}

function fetch_SubscriberPackageVersionId()
{
    PACKAGE_NAME="${1}" 
    VERSION_NUMBER=${2}
    PROJECT_PATH=${3}
    NAMESPACE=${4}
    LOG_INFO "Fetching SubscriberPackageVersionId for ${PACKAGE_NAME} with Verison Number: ${VERSION_NUMBER}"
    cd ${PROJECT_PATH}
    sfdx force:package:version:list -v ${HUB_USERNAME} --json > ${VERSION_NUMBER}.json
    
    if [ -n "${NAMESPACE}" ]; then
        PACKAGE_VERSION_ID=$(cat ${VERSION_NUMBER}.json | jq ' .result[]| select((.Version == "'"${VERSION_NUMBER}"'") and select(.Package2Name == "'"${PACKAGE_NAME}"'") and select(.NamespacePrefix=="'"${NAMESPACE}"'"))' | jq .SubscriberPackageVersionId -r )
    else
         PACKAGE_VERSION_ID=$(cat ${VERSION_NUMBER}.json | jq ' .result[]| select((.Version == "'"${VERSION_NUMBER}"'") and select(.Package2Name == "'"${PACKAGE_NAME}"'") and select(.NamespacePrefix==null))' | jq .SubscriberPackageVersionId -r )
    fi
    LOG_INFO "SubscriberPackageVersionId : ${PACKAGE_VERSION_ID}"
    echo "${PACKAGE_VERSION_ID}" > "${PACKAGE_NAME}".TXT
}

function delete_ScratchOrg()
{
    SCRATCH_ORG_NAME=$1
    HUB_USERNAME=$2
    #sfdx force:org:delete -u MyOrgAlias -p
    LOG_INFO "Deleting Scratch Org: ${SCRATCH_ORG_NAME}"
    sfdx force:org:delete -u "${SCRATCH_ORG_NAME}" --noprompt --targetdevhubusername "${HUB_USERNAME}"
    LOG_INFO "Deleted Scratch Org: ${SCRATCH_ORG_NAME}"
}