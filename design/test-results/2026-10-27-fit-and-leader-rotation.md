# Rotation on the leader's page and fit-to-width – 2026-10-27

guiDesign D-42 on the leader's page and D-28 as built in v0.45; FR-DISP-04. The A52 led; the Huawei sang. The owner turned the A52 by hand. **Result: both work as specified, after one fix to the line keeping on the leader's page.**

| Step | Result |
|---|---|
| Leader's page scrolled to *daar was laatst een meisje loos,* at the top; A52 turned to landscape | **Failed:** *5/9 · Drie schuintamboers* at the top. After the activity is made anew the leader's page reads its lyrics again, so the songs are short at first; the list's own pixel offset then carried it past song 3, which was never laid out, and the saved line could not be found. |
| Fix: scroll to the saved song first, wait for its line, then place it; A52 in portrait with *Daar was laatst een meisje loos,* at the top; turned to landscape | The same line at the top, the right song |
| Huawei, *De boer had maar ene schoen* at 28 sp, *Passend* on | The lyrics at 18 sp, two steps smaller; the longest lines still wrap at the Huawei's 1080-pixel portrait width, as D-28 says when nothing fits |

**Seen, not caused by this change:** the Huawei took 40 s to show the pushed song; its first repair connections failed with "status -1: no answer", as its Bluetooth stack has done before. A phone call was going on on the A52 at the time.

**Not tested:** *Passend* in landscape on a singer, where it is on by default; turning back to portrait on the leader's page.
