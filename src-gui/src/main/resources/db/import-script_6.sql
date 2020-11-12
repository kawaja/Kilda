INSERT INTO "VERSION" (Version_ID, Version_Number, Version_Deployment_Date)
VALUES (6, 6, CURRENT_TIMESTAMP);
	
INSERT  INTO "ACTIVITY_TYPE" (activity_type_id, activity_name) VALUES 
	(25, 'RESYNC_FLOW');
	
INSERT INTO "KILDA_PERMISSION" (PERMISSION_ID, PERMISSION, IS_EDITABLE, IS_ADMIN_PERMISSION, STATUS_ID, CREATED_BY, CREATED_DATE, UPDATED_BY, UPDATED_DATE,DESCRIPTION) VALUES 
	(137, 'fw_flow_resync', false, false, 1, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 'Permission for flow management -> re sync flow');
	
INSERT INTO "ROLE_PERMISSION" (ROLE_ID,PERMISSION_ID) VALUES 
	(2, 137);