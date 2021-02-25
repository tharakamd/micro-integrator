/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.mi.migration.migrate;

/**
 * This class holds common constants for password migration client.
 */
public class MigrationConstants {

    public static final String CARBON_HOME = "carbon.home";
    public static final String MIGRATION_DIR = "migration";
    public static final String MIGRATION_CONF = "migration-conf.properties";
    public static final String KEYSTORE_PASSWORD = "keystore.identity.key.password";
    public static final String KEYSTORE_LOCATION = "keystore.identity.location";
    public static final String SECURE_VAULT_PATH = "/_system/config/repository/components/secure-vault";
    public static final String ADMIN_USERNAME = "admin.user.name";
    public static final String RSA = "RSA";
    public static final String CIPHER_TRANSFORMATION_SYSTEM_PROPERTY = "org.wso2.CipherTransformation";
}
