# Field Research \+ Product Strategy

# Snabbit, beyond the booking

# *Designing for the moments that make home services worth booking again.*

# 01 \- CONTEXT

### How Snabbit is used in India right now

*This is what I’ve observed.*

I kept noticing two different reasons people open Snabbit. One needs help today. The other needs to know help will show up again tomorrow.

The first person has a problem right now. Their regular house help is away. Guests are coming. Or they have finally looked around the flat and realised it needs cleaning. They are not planning a routine. They want someone to come today, do the job, and let them get on with their day.

Then there is the person who already knows they will need help next week. A shop owner wants the floor cleaned before opening. A founder wants the office ready each morning. A restaurant owner wants the tables and kitchen sorted before customers walk in. They do not want to make the same booking every night. They want to choose the days, know who is coming, and stop thinking about it.

The quick booking flow makes sense for the first person. For the second, speed matters less than knowing the same person will arrive at the right time and already understand the place.

So I looked at what happens after they tap Book.

# 02 \- FIELD RESEARCH

### *What I kept hearing from people who need help regularly*

*I looked at the complaints people posted and thought about the visits in my own family. Four things kept coming up: planning the next visit, knowing who would arrive, telling the worker what to expect, and getting the service started once they were there.*

> ***Routine***  
>  ***“Let me book the whole week at once”***  
>  *If you need the shop cleaned every morning, booking it five times feels silly. You already know the days and the time. You probably know which worker you want, too. The useful thing would be to set it up once and get on with running the shop.*

> ***Quality***  
>  ***“When my favourite isn’t free, the work drops”***  
>  *People get used to someone who knows their home. When that person is unavailable, the replacement may need every instruction again. A few customers described work that felt rushed or below what they expected. The booking looks the same on screen, but the visit feels different.*

> ***Fit***  
>  ***“The worker arrived, and then found out”***  
>  *Imagine a worker reaching the door and only then learning there are pets inside, or that the job involves non-vegetarian utensils they are not comfortable handling. The customer assumed it was fine. The worker had no chance to say otherwise. Now both people are standing there trying to work it out.*

> ***Access***  
>  ***“The OTP went to someone who wasn’t there”***  
>  *Sometimes the person who books will not be there when the worker arrives. That happens in homes too: a son books for his mother, or a colleague books for the shop. If the check-in code goes to the booker, someone has to call them before the worker can begin.*

***The pattern***  
 *That was the pattern I kept coming back to. The tap to book is only the beginning. The visit works when the right worker arrives prepared, and the person at the door can let them start.*

***03 — SOMETHING I NOTICED WHILE USING SNABBIT***

### *The booking is quick. The handoff at the door can still go wrong.*

*Two small moments made this real for me.*

*First, I want to say thank you. Snabbit has really helped my mom with cooking and cleaning, and it’s just one tap away. But my mom isn’t comfortable with apps, so it’s always me or my sister who books for her. And when the worker arrives, we’re usually not home. The OTP comes to our phones, not hers. So she calls us, and we miss it because we’re in a meeting or the phone is on silent. The worker waits at the door, and sometimes my mom even tries the support line, which is harder for her than the app itself. Booking took a few seconds. Starting the service took 10 minutes.*

*The second thing came from my neighbour. She has two pet pitbulls, and they aren’t comfortable around strangers. When she booked Snabbit, the dogs were leashed, but they kept barking and jumping at the door. The worker got scared, did the bare minimum, and waited for the time to get over. Nobody did anything wrong. The app simply never asked about her pets, and the worker never knew.*

*One problem is a code on the wrong phone. The other is a worker who did not know about the dogs. Both happen because a detail that matters at the door was missing earlier.*

*That changed how I thought about the problem. I wanted the person at home to receive the OTP, and the worker to know the conditions of the visit before arriving.*

# 04 —WHAT PEOPLE ARE SAYING

### What customers say after the booking

*I also looked at what customers were saying in public. Their posts put words to the problems I had noticed: uncertain quality, late changes, and support that did not always resolve the issue.*  
**![][image1]**

# 

# 05 — WHAT I FIXED

### *Four moments I redesigned*

### *I built a prototype around those four moments. I wanted to see how much of the uncertainty could be removed before the worker reached the door.*  PRIORITY 1 — ROUTINE

### ***Pick the days. Pick the time. Pay once.***  *“Let me book the whole week at once”*

### *Snabbit is very good at the one-off booking. Pick a service, choose a slot, and get on with your day. That works when you need help once.*

### *But a shop owner opening every morning already knows the job will be there again tomorrow. So I added a way to choose several days at once, pick a worker for each visit, and pay in one checkout.*

### *What I built*  *A third option on the Schedule screen, next to Today and Tomorrow: Multi-day.*

1. ### *Tap Multi-day and pick 2 to 10 days.*

2. ### *Choose the duration for each day.*

3. ### *Choose a time slot for each day.*

4. ### *Pick your worker for each visit, ideally the same trusted person.*

5. ### *Check out once, with one payment.*

### *I built it as an extension of the schedule flow on purpose. Anyone who has booked before already knows how to use it.*

![][image2]![][image3]  
![][image4]![][image5]  
![][image6] ![][image7]

PROBLEM 2 — QUALITY

*Your favourite first. A trained backup. Your call.* 

*When someone has a worker they trust, a replacement is a gamble. They may be excellent, but the customer cannot tell from a name and a photo. I wanted them to see more before making that choice.*

*What I built*  
 *In the prototype, I grouped workers into three tiers using experience, training, and customer ratings. The exact rules would need real worker data before launch.*

1. *Expert: 9+ months of experience in this prototype.*

2. *Veteran: an experienced group with a shorter track record.*

3. *Standard: workers earlier in their experience.*  
   *The labels are only useful if Snabbit can back them up with real training and performance data.*

*Pick your level, pay for what you need*  
 *The customer can see the tier and price before choosing. An everyday task may not need the highest tier. For a more demanding job, they may gladly pay more for someone with a longer track record.*

*Snabbit+ and favourites*  
 *Snabbit+ is a 3-week pass that lets users book multiple days with a worker they trust. Users can save up to 3 favourites, ranked by the ratings they give. Before checkout, they see their favourites if free, then other available workers sorted by tier.*

*When a favourite isn’t free*  
 *The app suggests the next best available worker with a similar rating and asks the user to confirm. Nothing changes without a yes.*

*![][image8]  ![][image9]*

![][image10]

PROBLEM 3 — FIT

*Ask less. Match better. No surprises at the door*. 

*A partner accepts the job, reaches the house, and only then finds something they’re not comfortable with: non-veg utensils, pets, or a personal or religious limit. The household details were missing at the moment they could have helped both sides. The result is an awkward visit or a cancelled one, and frustration on both sides.*

*My idea: ask only when it matters*  
 *I did not want to put a long questionnaire in front of every customer. Most of those questions will not matter for most visits. So the app asks about the home only after the customer chooses the service and time, when the answers can actually help them choose a worker.*

*What I built*

1. *After the user picks the days, duration, and slots, a bottom sheet appears: “Check your home and visit details.”*

2. *It asks only what matters for the chosen service: the tasks needed, pets at home, non-veg utensils, equipment available, special requirements like stairs, and heavy lifting.*

3. *For the prototype, I gave each demo worker a set of comfort limits. In a live service, workers would confirm these themselves.*

4. *The app compares those answers with each worker’s limits and marks the ones that fit.*

5. *The list still shows workers whose preferences differ, with a clear warning on their cards. The user can review each worker’s limits before choosing, and the visit details are shown again at checkout.*

![][image11] ![][image12]![][image13]

PROBLEM 4 — ACCESS

*Right person. Right phone. Right OTP.*   
*In shops and restaurants, the owner often isn’t the one who books. A colleague does. So when the worker arrives, the OTP may be on the booker’s phone, miles away from the door. The worker has to wait while someone tries to reach them. The worker waits, the business loses time, and nobody is at fault. The OTP simply went to the wrong person.*

*My idea: send it to whoever is actually there*  
 *This happened to my family, so I kept the fix simple: let the person booking say who will actually be home.*

*What I built*

1. *At checkout, a new section asks: “Who will be at home?”*

2. *The user adds a name and a mobile number for the person who will receive the worker.*

3. *If they leave it blank, the app uses the booker’s own verified details, so nothing slows down for people who don’t need it.*

4. *When the worker arrives, the check-in OTP goes to that person’s phone.*

5. *The service starts only when the OTP is entered, so the right person is present and the worker is safe too.*

![][image14]

06 — CAMPAIGN IDEAS

Some ideas I had

*If I were part of the team, this is what I'd want to work on.*

IDEA—01  
Fire the auto-reply.

Customers are already explaining what went wrong in public. I saw replies that apologised and asked for a phone number, but did not address the problem in the post. I would give the support team room to answer the actual issue: what happened, what happens next, and when the customer will hear back. If Snabbit caused the cancellation, the customer should not have to argue to get the charge reversed.

Something for us to figure out together: which complaints always need a person, and which ones a quick automated answer can handle.

IDEA— 02

### Ads can’t ring your doorbell. People can**.**

A home service is personal. I would rather hear a neighbour say “I book her every week” than watch another perfect-looking ad. Let customers tell the story in their own words. Show a real worker and a real visit.  
Then show up every day, the way California Burrito and Instamart do. They give people a reason to reply, share, and come back. Snabbit has the same material: messy-house confessions, guests-arriving-in-an-hour panic, a sink nobody’s proud of. Post something light daily, repost real customers, and reply like a friend. Keep serious support separate, so nobody feels mocked when they have a real problem. It’s an experiment, but I think it’s worth running

---

    *These were the insights, gaps, and ideas I could find.*

I want to be the person who helps *Snabbit* earn trust*,*                                        every visit*.*

 *I’m early in my career. What I can bring is a close view of the everyday moments that shape whether someone books again. I’ve watched my mom use Snabbit, and I’ve seen what makes her trust it, and what makes her call me when my phone is on silent and I’m out with friends. I built a prototype around those moments, always asking the same question: what would make someone comfortable booking again? I’d love to bring that thinking to Snabbit. Happy to get on a call .*