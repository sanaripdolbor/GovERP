printf "%0.s=" {1..100};printf "\n"
echo "Running cron-job foo at $(date)"
pwd=$(pwd)
cd $pwd
echo $pwd
source ./config.txt
echo $SCRIPT_NAME

response=$(curl --write-out '%{http_code}' --silent --output /dev/null "${URL_BASE}/${URL_LOGIN}")
if [ $response -ne 200 ]
then
	echo "tomcat disabled"
	exit 0
fi
echo "tomcat enabled"
touch $TEMP_FILE
truncate -s 0 $TEMP_FILE

curl -X POST -c $TEMP_FILE -H $CONTENT_TYPE -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}" "${URL_BASE}/${URL_LOGIN}"

NEW_MODELS=$(curl -X GET -b $TEMP_FILE "${URL_BASE}/ws/meta/check/models")
NEW_FIELDS=$(curl -X GET -b $TEMP_FILE "${URL_BASE}/ws/meta/check/fields")

if [ $NEW_MODELS -eq 0 ] && [ $NEW_FIELDS -eq 0 ]
then
	echo "not found new models and fields"
	exit
fi

cd ..
# clean
./gradlew clean --no-daemon
echo "clean"
if [ $NEW_MODELS -gt 0 ]
then
	./gradlew generateXml --no-daemon
	echo "created xml"
fi

if [ $NEW_FIELDS -gt 0 ]
then
	./gradlew generateField --no-daemon
	echo "created field"
fi
# build
echo "build"
./gradlew build --no-daemon
if [ $? -ne 0 ]
then
	echo "ERROR: application not build"
	exit
fi

# restart
systemctl stop tomcat
echo "tomcat stopping"

rm $PATH_TOMCAT/$WAR_NAME
cp build/libs/$WAR_NAME $PATH_TOMCAT
systemctl start tomcat
echo "tomcat starting"
exit
