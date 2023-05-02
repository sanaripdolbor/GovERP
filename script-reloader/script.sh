printf "%0.s=" {1..100};printf "\n"
echo "Running cron-job foo at $(date)"
pwd=$(pwd)
cd $pwd
echo $pwd
source ./config.txt
echo $SCRIPT_NAME

response=$(curl --write-out '%{http_code}' --silent --output /dev/null "${URL_TOMCAT}")
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
if [[ "$NEW_MODELS$NEW_FIELDS" =~ [0-9] ]]
then
	echo "not found new models and fields"
	exit
fi
echo "$NEW_MODELS models -- $NEW_FIELDS fields"
cd ..
# clean
./gradlew clean -w --no-daemon
echo "clean"
if [ $NEW_MODELS -gt 0 ]
then
	./gradlew generateXml -w --no-daemon
	echo "created xml"
fi

if [ $NEW_FIELDS -gt 0 ]
then
	./gradlew generateField -w --no-daemon
	echo "created field"
fi
# build
echo "build"
./gradlew build -w --no-daemon
if [ $? -ne 0 ]
then
	echo "ERROR: application not build"
	exit
fi

rm $PATH_TOMCAT/$WAR_NAME
cp build/libs/$WAR_NAME $PATH_TOMCAT
exit
