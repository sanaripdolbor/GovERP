# Script for tomcat reload

## Links
- [Description](#Description)
- [Configuration](#Configuration)

### Description

As you know, first the _models_ and _fields_ are stored as **json**, after running the **script**,
 the **xml** is generated based on the **json**.

This script doing (linux Ubuntu 22.02)
1. Check **tomcat** status (enabled or disabled)
2. Check new _models_ and _fields_
3. If exists, `./gradlew clean`
4. If newModels > 0 `./gradlew generateXml`
5. If newFields > 0 `./gradlew generateField`
6. Building project `./gradlew build`
7. Disable **tomcat**
8. Delete _old war_ file
9. Copy _new war_ in **tomcat/webapps**
10. Enable **tomcat**

And for the script to work properly, need
+ curl
+ tomcat.service (for `systemctl`)
+ check executable permissions `gradlew.sh` script

### Configuration

The configuration file is called `./config.txt`
There you need to configure paths to folders, files and url

|name|desc|
|---|---|
|URL_TOMCAT|tomcat url for `curl` request|
|PATH_TOMCAT|**tomcat/webapps** directory path in linux|
|WAR_NAME|static name war file (for find in **tomcat**)|
|URL_BASE|full url path to **web-app**|
|URL_LOGIN|url path to **axelor** security (for authorization)|
|TEMP_FILE|file name (for saving **axelor** cookie)|
|CONTENT_TYPE|set **curl** request content-type|
|USERNAME|**axelor** admin login|
|PASSWORD|**axelor** admin password|
|SCRIPT_NAME|title for script, need for logs|
