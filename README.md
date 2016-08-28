# Annuus Media Server(AMS)
AMS is an Open Source Flash Media Server written in Java.
it now supports the following features: 
 1.RTMP and RTMPT Protcol 
 2.HTTP Protcol 
 3.AMF0/AMF3 
 4.Streaming Audio/Video (FLV, MP4(H264/AAC), F4V) 
 5.Recording Live Streams (FLV) 
 6.Live Stream Publishing(VP6, H264)
 7.Live Stream Replication(TCP unicast) 

## Setting
The name of configure file is server.conf. 
1. Worker setting
    server.dispatchers=4       ;number of worker thread to read packet data from client
    server.workers=16          ;number of worker thread to handle http or rtmp request
    server.mempool.size=200    ;set memory pool size to 200M, must less than -Xmx setting of JVM
2. HTTP setting
    http.host=0.0.0.0   ;IP address of http server
    http.port=8080      ;listen port of http server
    http.root=www       ;root path of html file 
3. RTMP setting
    rtmp.host=0.0.0.0   ;IP address of rtmp server
    rtmp.port=1935      ;listen port of rtmp server
    rtmp.root=video     ;root path of video file
4. Live stream replication setting
    repl.ucast.host=0.0.0.0              ;IP address of TCP replication
    repl.ucast.port=1936                 ;listen port of TCP replication
    repl.ucast.master.host=0.0.0.0       ;IP adddress master server host
    repl.ucast.master.port=1936          ;listen port of master server host

## Run Server
    ./ams.sh

## Demo
   run server and open url http://localhost:8080/demo.html

## Utility
1. flvindex.sh
   create index file from a FLV video file.
2. videopublisher.sh
   publish a video file to media server.

## Release
version 0.0.1
   the first release.
version 0.1.0
  fix some AMF3 bugs.
version 0.1.1
  fix a buffer allocation bug.
version 0.2.0
  add H264/AAC streaming.
  fix some bugs.
  refactoring.
version 0.3.0
  RTMPT support
  FLV index file support
  slf4j logger support
  rewrite live stream replication
  refactoring

## Author
 qinyong2000@gmail.com
 