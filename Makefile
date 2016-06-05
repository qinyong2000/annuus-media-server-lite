VERSION = 0.3.0
BASEDIR = ./ams-$(VERSION)
PROGS = ams.jar README.md license.txt server.conf log.properties ams.sh ams.bat flvindex.sh flvindex.bat videopublisher.sh videopublisher.bat
WWWFILES = www/demo.html www/swfobject.js www/player.swf
VIDEOFILES = video/test.flv video/test.mp4

all: ams.jar
	mkdir $(BASEDIR) $(BASEDIR)/lib $(BASEDIR)/log $(BASEDIR)/www $(BASEDIR)/video
	cp $(PROGS) $(BASEDIR)
	cp lib/*.jar $(BASEDIR)/lib
	cp $(WWWFILES) $(BASEDIR)/www
	cp $(VIDEOFILES) $(BASEDIR)/video
	cp -r src $(BASEDIR)/src
	rm -rf `find $(BASEDIR)/src -name .svn`
	tar zcvf ams-$(VERSION).tar.gz ams-$(VERSION)

clean:
	rm -rf $(BASEDIR)
