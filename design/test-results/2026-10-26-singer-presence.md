# Singers who leave: swipe-away and the signed-in count – 2026-10-26

Option B of `design/proposals/2026-10-26-singer-presence.md` (BLE protocol v0.38, 10.9; guiDesign v0.37, D-62), on the A52 (leader) and the Huawei (singer). **Result: a singer whose app is swiped away from the recent apps sends the leave notice, and the leader's count drops at once.**

| Step | Result |
|---|---|
| 21:51:43 | The Huawei joined from the list; two connection attempts failed with status 133, the third registered at 21:51:48 (*Joined*); the leader showed *1 zanger* |
| 21:52:24.3 | The app swiped away in the Huawei's recent apps |
| 21:52:25.1 | `singer leave sent=true ms=1069`: the notice was out 0.8 s after the swipe |
| 21:52:32 | The leader showed *0 zangers* |

**Seen in the first attempt (21:47):** with the Huawei's Bluetooth stuck, every connection failed with status 133 and the singer never got its Join answer, while the leader had counted it. A singer without a token cannot sign out, so in that case the count stays high; option A of the proposal would cover it.
