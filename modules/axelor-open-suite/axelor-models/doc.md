How to install axelor-models in your [axelor][axelor] project
===
First you need to download the necessary files from github

### 1. Windows and Linux
You can just download it by clicking the button from the site
`Code -> Download Zip` Or `Code -> Copy repo link` after `git clone` in cmd or bash
### 2. Copy need folders
1. Copy folder with name **groovy** from `buildSrc/src/main`, and set in your project folder `buildSrc/src/main`
2. Copy folder with name **axelor-models** from `modules/axelor-open-suite`, and set in your project folder `modules/axelor-open-suite`
3. Copy folder with name **script-reloader** from _project_

After add file `temp.txt` in `.gitignore`, this for need script-reloader saving cookies

### 3. Configuration
Open **application.properties** from `src/main/resources/application.properties`, and write configs
```properties
# xgtool
xgtool.name=custom-model
xgtool.path=com.axelor.apps.custom.db
xgtool.generate_path=modules/axelor-open-suite/axelor-models/src/main/resources
context.xgtool=custom-model
```
|name|desc|
|---|---|
|xgtool.name|**module** name for generated xml
|xgtool.path|**module** path for generated xml
|xgtool.generate_path|**path** for generated xml
|context.xgtool|for action which set module name

> if you want to change the `context.xgtool`, don't forget to go to the file `modules/axelor-open-suite/axelor-models/src/main/webapp/js/metaJsonModelController.js` and change it too

### 4. Set Cron job
Cron job need for start script.sh in `script-reloader/script.sh`
```bash
crontab -e

SHELL=/bin/bash
EMAIL=EXAMPLE@EXAMPLE.EXAMPLE
0 3 * * * /home/user/Projects/axelor-template/script-reloader/script.sh >> /home/user/cron.log 2>&1
```
This script start 03:00 PM, and save log in `/home/user/cron.log`.

[axelor]: https://axelor.com/