@echo off
set MS_VERSION=131
set MS_SUBVERSION=0
set WZ_XML_PATH=C:\Users\elfenlied\Desktop\Elfenlied\EmuDev\JMS\Server\RireSaba\jms_wz\v%MS_VERSION%\
@title かえでサーバー v%MS_VERSION%.%MS_SUBVERSION%
set CLASSPATH=.;dist\*
java -server -Dnet.sf.odinms.wzpath=%WZ_XML_PATH% -Djavax.net.ssl.keyStore=filename.keystore -Djavax.net.ssl.keyStorePassword=passwd -Djavax.net.ssl.trustStore=filename.keystore -Djavax.net.ssl.trustStorePassword=passwd tools.wztosql.DumpItems -update
java -server -Dnet.sf.odinms.wzpath=%WZ_XML_PATH% -Djavax.net.ssl.keyStore=filename.keystore -Djavax.net.ssl.keyStorePassword=passwd -Djavax.net.ssl.trustStore=filename.keystore -Djavax.net.ssl.trustStorePassword=passwd tools.wztosql.DumpMobSkills -update
java -server -Dnet.sf.odinms.wzpath=%WZ_XML_PATH% -Djavax.net.ssl.keyStore=filename.keystore -Djavax.net.ssl.keyStorePassword=passwd -Djavax.net.ssl.trustStore=filename.keystore -Djavax.net.ssl.trustStorePassword=passwd tools.wztosql.DumpQuests -update
pause
