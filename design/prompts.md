
# 18-10-2026 Birth of an pp
The original prompt that results in the first Functional Design Specification (FDS):

```
We are going to develop an Android app (wich later can also be an Apple app) for a songbook (Zangbundel) The app multi langual, primary Dutch and secondary English but other languages can be added easily later. De app has access to a external libray of lyrics in a git repository that it can import and update locally. this way copyrights and licnces can easilty be managed outside of the app. the app is used at a campfire (for example at scouting) where there is one app that is the campfire leader. this app starts the gathering, and sends a beacon (broadcast) over ble for the duration of th campfire. The participants of he campfire when sitting at the fire join the sonbook gathering by receiving the BLE invitation and joining. in their app the find teh lyrics that will be singed that are provided over ble by the leader when sung. this way everybody gets the lyrics offered at the same time. the app can be black text on white background and during drajnes white text ion black background works in portrait and landscape and can easly be scroled line by line. the app is not depandant on internet access and works completely over ble but when internet is available lyrics can be downloaded when directed at by the fir elieader. in a second phase the app shall support caraoke mode where the particpants are aided in zinging the lyrics. 
that app shall be able to support notes for instruments in a later phase. 
particpants shall be able to submit requests to the leader for songs they wanna sing that are sent to them by the leader. a particpant can extit the ble gathering anytime. the kampfire is inded by the leader leaving all singer apps. at leaving the lyrics are discarded in the app. maybe the list of song titles remains as a memory.
create a functional requirements document in design/
```

# 18-10-2026 App distribution
The prompt that added section 8.5 and Appendix A to the FDS (v0.4):

```
add to the fds that the leader can share a link to the app to participants to install the app form the play store throug QR-code. investigate is other means of distribution of the app are possible without internet.
```
