#--------------------------------------------------------------------------------------------------------------#
# Summary: This Makefile does following things:
# - build: Build the Docker Image
# - push: Push the image in to Artifactory Docker Registry
# - clean: Remove the image and dist folder from local
#--------------------------------------------------------------------------------------------------------------#

SHELL := /bin/bash

INFO=$(shell echo `date +%Y-%m-%d-%H:%M:%S` [INFO])
ERROR=$(shell echo `date +%Y-%m-%d-%H:%M:%S` [ERROR])
WARN=$(shell echo `date +%Y-%m-%d-%H:%M:%S` [WARN])

authenticate-DevHub:
	@echo "$(INFO) Authenticate to DevHUb"
	@source make_script.sh; authenticate_DevHub "$(CONSUMER_KEY)" "$(HUB_USERNAME)" "$(JWT_KEY_FILE)" 
	@echo "$(INFO) Authentication done"

create-SFDX-Project:
	@echo "$(INFO) Creating SFDX project"
	@source make_script.sh; create_Project "$(PROJECT_NAME)" "$(WORKSPACE)"   
	@echo "$(INFO) SFDX project created "

create-scratchORG:
	@echo "$(INFO) build-scratchORG:"
	@source make_script.sh; create_scratchORG "$(SCRATCH_ORG_NAME)" "$(SCRATCH_ORG_DURATION)" "$(ORGNAME)" "$(EDITION)" "$(PROJECT_PATH)" "$(SCRATCH_DEFINITION_FILE_PATH)" "$(HUB_USERNAME)" "$(NAMESPACE)" "$(SCRATCH_ORG_DETAILS_TEMP_FILE)" "$(SCRATCH_USERNAME_TO_CREATE)" "$(API_VERSION)" "$(ADMIN_EMAIL)"
	@echo "$(INFO) scratch org created"

delete-ScratchOrg:
	@echo "$(INFO) delete-ScratchOrg:"
	@source make_script.sh; delete_ScratchOrg "$(SCRATCH_ORG_NAME)" "$(HUB_USERNAME)"
	@echo "$(INFO) scratch org deleted"

convert-to-SfdxFormat:
	@echo "$(INFO) coverting project to SFDX format"
	@source make_script.sh; convert_to_SfdxFormat "$(PROJECT_PATH)" "$(SRC_PATH)"
	@echo "$(INFO) converted to SFDX."

deploy-to-ScratchOrg:
	@echo "$(INFO) deploying to Scratch Org"
	@source make_script.sh; deploy_to_Scratch "$(SCRATCH_ORG_NAME)" "$(PROJECT_PATH)" "$(SFDX_FOLDER_PATH)"
	@echo "$(INFO) deploy-all."

run-unit-test:
	@echo "$(INFO) running unit test"
	@source make_script.sh; run_apex_test "$(SCRATCH_ORG_NAME)" "$(TESTLEVEL)" "$(PROJECT_PATH)"
	@echo "$(INFO) done with unit test."

import-test-data:
	@echo "$(INFO) importing test data to scratch org"
	@source make_script.sh; import_test_data "$(IMPORT_TEST_DATA_PATH)" "$(PROJECT_PATH)" "$(SCRATCH_ORG_NAME)"
	@echo "$(INFO) data imported to scratch org."

createPackageVersion:
	@echo "$(INFO) creating Package with namespace"
	@source make_script.sh; createPackageVersion "$(PACKAGE_NAME)" "$(PACKAGE_TYPE)" "$(PROJECT_PATH)" "$(HUB_USERNAME)" "$(NAMESPACE)" "$(INSTALLATION_KEY)" "$(WAIT_TIME)" "${API_VERSION}" "${SCRATCH_DEFINITION_FILE_PATH}" "${VERSION_NUMBER}" "${SFDX_PROJECT_JSON_PATH}" "${IS_PROJECT_ALREADY_IN_SFDX_FORMAT}" "$(SFDX_FOLDER_PATH)"
	@echo "$(INFO) package created with namespace."

installPackage:
	@echo "$(INFO) installing Package to QA Scratch Org"
	@source make_script.sh; installPackage "$(PACKAGE_NAME)" "$(PROJECT_PATH)" "$(SCRATCH_ORG_NAME)" "$(INSTALLATION_KEY)" "$(HUB_USERNAME)"
	@echo "$(INFO) package installed."

post-installPackagelist:
	@echo "$(INFO) installing list of required Packages to QA Scratch Org"
	@source make_script.sh; post_installPackageList  "$(PROJECT_PATH)" "$(SCRATCH_ORG_NAME)" "$(INSTALLATION_KEY)" "$(POST_INSTALLATION_PACKAGE_FILE_PATH)"
	@echo "$(INFO) package installed."

deploy_additional_Component_to_Scratch:
	@echo "$(INFO) deploying additional component to Scratch Org to QA Scratch Org"
	@source make_script.sh; deploy_additional_Component_to_Scratch "$(SCRATCH_ORG_NAME)" "$(ADDITIONAL_SRC_PATH)"
	@echo "$(INFO) deployed."

execute-Code-in-devconsole:
	@echo "$(INFO) Exectuing the given script in the created Scratchorg through Tooling API"
	@source make_script.sh; execute_Code_in_devconsole "$(SCRATCH_ORG_NAME)" "$(PROJECT_PATH)" "$(CODE_SNIPPET_FILE_PATH)"
	@echo "$(INFO) Completed the step."


.PHONY: authenticate-DevHub create-SFDX-Project create-scratchORG deploy-to-ScratchOrg run-unit-test convert-to-SfdxFormat createPackageVersion installPackage post_installPackagelist deploy_additional_Component_to_Scratch execute-Code-in-devconsole
