# Dynamo Room

An Android game about running a late-1920s gasoline-engine electric light plant
by hand. There is no governor, no voltage regulator, no automatic synchroniser
and no tutorial. There is an engine, a switchboard, and a dispatcher who expects
his kilowatts on time.

You are the night operator at the Millbrook Light & Power Company. A **six
cylinder** horizontal gasoline engine drives a 12-pole, 2300 volt, 60 cycle
alternator rated **500 kilowatts**, and that set is tied into a full
interconnection through a main transformer and a unit breaker.

The grid is large. Even 500 kilowatts is small against it, so you do not set its
frequency — you follow it. What you do control is how much you put onto
the bars, and the dispatcher hands you a new load order every ninety seconds.

The switchboard is not for the grid. It is for the plant's own internal
supplies, and there are two of them. The **main bus** carries the regular
running gear — the control supply, the circulating pump, the oil pump, the house
lights — and hangs off the generator terminals through the station transformer,
so it is alive whenever those terminals are: from your own machine, or from the
system back-feeding through the starting transformer. The **emergency line**
carries the four things the set cannot run without — the ignition, the
excitation, the emergency pump and the emergency lights — and is normally held
up by the battery.

## The controls

**The five mains**

| Control | What it does |
|---|---|
| Supply selector | `GRID · GEN · OFF · EMG`, in that physical order |
| Throttle | The only speed control there is, and therefore the frequency control |
| Spark lever | −5° to +38° before top dead centre |
| Mixture | 17.5:1 lean to 9.5:1 rich, as supplied to the intake |
| Field rheostat | Excitation: terminal volts off the bus, reactive load on it |

**Switchboard (internal supplies)**: the unit breaker out to the grid, and two
rows of knife switches with cartridge fuses.

| Main bus row | | Emergency row | |
|---|---|---|---|
| Station TX breaker | — | Emergency TX breaker | — |
| Starting TX breaker | — | Battery breaker | — |
| Control supply | 1.2 kW | Field switch | — |
| Circulating pump | 14.0 kW | Ignition | 0.6 kW |
| Oil pump | 5.5 kW | Excitation | 2.2 kW |
| Fuel transfer pump | 3.5 kW | Emergency pump | 4.0 kW |
| House lights | 3.0 kW | Emergency lights | 1.2 kW |

**Starting gear**: relief cock, primer, and the electric starting motor, which is
wired through the `EMG` position only.

**Per cylinder**, six of each: a sight feed off the lubricator, and an igniter
cut-out switch.

**The tanks**: the fuel cock, the transfer pump switch, the jacket water make-up
valve and the hand pump on the oil drum.

**Auxiliaries**: cooling water gate and the master stroke on the lubricator.

## The three decks

The instrument board across the top is always in view: synchroscope, cycles,
the three synchronising lamps, machine and bus voltmeters, wattmeter, ammeter.
Below it the panel switches between three decks, which is the walk an operator
makes round the station:

- **CONTROL** — three panels. **ENGINE** carries the tachometer, the supply
  selector, throttle, spark, mixture and the starting gear. **OIL AND WATER**
  carries the oil pressure and jacket gauges with the lubricator and water gate
  handwheels. **GENERATOR** carries the field rheostat with field and reactive
  meters and the power factor.
- **ENGINE** — the six cylinders, each with its exhaust pyrometer, its sight
  feed and its igniter cut-out; and under them the gauge glasses on the fuel day
  tank, the jacket water header and the oil sump, with the cock, the transfer
  switch, the make-up valve and the oil hand pump.
- **ELECTRICAL** — the station single line, the relay panel, and under them the
  switchboard: the unit breaker and two rows of knife switches.

A red pip appears on whichever tab you are not looking at when something over
there wants attention. The annunciator strip along the foot is always live.

## The single line

The mimic diagram on the electrical deck is drawn the way the board really is
wired, and it shows what is actually connected to what. Live conductors run
bright with current beads sliding along them; dead ones go grey; open contacts
show as a blade swung clear of its jaws.

```
  == UNIT H.T. ==+== UNIT BREAKER ==+====== GRID 2300 V ======
        |                                            |
     MAIN TX                                  START TX BREAKER
        |                                            |
        |                                      STARTING TX
        |                                            |
  ======+========= GENERATOR TERMINALS ==============+=======
        |        |                    |              |
     [ GEN ]  STN TX BKR         EMG TX BREAKER     (GRID)
                 |                    |              |
             STATION TX         EMERGENCY TX         |
                 |                    |              |
  ===== MAIN BUS =====                +--- BATTERY   |
   | |  |   |   |                     |        |     |
  CTRL | OIL  FUEL LIGHT            (GEN)   BATT BKR |
     CIRC                             |        |     |
                                      |     (EMG)    |
                                      |        |     |
  ================= EMERGENCY LINE ====+========+=====+==
    |      |       |       |        |
   IGN    EXC   E.PUMP   E.LT   FIELD SW
   0.6    2.2     4.0     1.2
```

The high tension bar is sectionalised at the unit breaker. The short left
section is the machine's own — the main transformer up from the generator
terminals, and nothing else on it. Everything right of the breaker is the
system, and that is what the starting transformer hangs on.

**The starting transformer is the back-feed road.** Its secondary lands on the
generator terminals — the orange bar the whole station hangs off. Close its
breaker on a dead station and the system holds that bar up: the main bus comes
alive, the circulating pump and the oil pump turn, the lights come on and the
charging set starts putting the battery back, all before the engine has turned a
revolution. That is how you bring a cold plant in, and it is why a flat battery
is a nuisance rather than the end of the shift.

**And it must be opened before you excite.** With its breaker in, your machine's
terminals are already tied to the system through it. Bring the field up against
that and you are paralleling the set through a transformer meant for lighting a
dead station, out of step. It will not hold: its own protection throws it off.
No damage, but it is a bang, and it is avoidable. Open it, then the field, then
synchronise properly on the unit breaker.

The mimic is coloured the way a real control room panel is: **red** for the
high tension side, **orange** for generator voltage and the main bus, **green**
for the emergency circuit. Every switching device carries a lamp, green when it
is made and red when it is open.

**The two internal supplies never touch.** They are separate all the way back
to the generator terminals, each with its own transformer, and nothing ties one
to the other. Losing the main bus costs you the pumps and the lights; losing the
emergency line stops the engine.

**The main bus** is fed off the generator terminals through the station
transformer, and nothing else feeds it. So it is alive exactly when that bar is
alive: from the system while the starting transformer is in, and from your own
machine once it is out. Islanded and unexcited there is no control supply, no
circulating pump, no oil pump and no house lights, which is why getting the
field up is part of starting rather than part of synchronising.

**The emergency circuit comes straight off the generator terminals too**, on
its own breaker and its own transformer. That transformer's output does two
things: it charges the battery, and it is what the `GEN` position of the
selector puts on the emergency line. The battery floats across the same output
and goes out to the line through the battery breaker.

That is the only road the charge takes back to the cells, so with the emergency
transformer breaker open the battery is islanded and will only ever run down —
and an islanded, unexcited machine gives its transformer nothing to work on, so
the field you need for charging comes off the line you are charging. (With the
starting transformer in, the system does that job for you, which is how a flat
battery gets brought back.) Open the battery breaker and the cells are off the
line altogether: no battery ignition, and no starting motor.

Only one of the three taps into the emergency line is made at a time, and the
selector is what makes it. Each load has its own switch and fuse on the board.
Watch the battery branch: it runs one way when you are drawing off `EMG` and the
other way when the emergency transformer is putting the cells back.

**The emergency line has a hard limit.** The grid will carry 60 kW into it and
the emergency transformer 40 kW, both more than it asks for. The battery will carry 7 kW,
which is barely enough — the ignition, the field and the emergency pump come to
6.8 kW of it. Switch the emergency lights in as well and it goes over. Overload
it far enough and the volts sag until loads drop out, and then a fuse goes.

**The excitation is a load on that line.** Pull that switch, or let the line
collapse, and the field goes with it — which off the bus means no volts, on the
bus means a pole slip, and on `GEN` means the emergency transformer dies and
takes the line with it.

**There are two pumps and they are on different buses.** The gate valve on the
control deck meters the flow, but the circulating pump on the main bus is what
provides it. The emergency pump on the battery's line gives about four tenths of
that, which is enough to nurse a cold engine and not enough to run one. With
both switched out the gate does nothing at all.

**The oil pump is on the main bus behind the mechanical lubricator.** Lose it
and only what gravity will carry reaches the bearings, so the feed has to be
opened up to make up for it.

## The relay panel

Five relays sit in a row under the mimic, each behind a little window with a
painted flag in it. When a relay operates it **trips the unit breaker** and
**drops its target**, and the target stays dropped after the relay itself has
reset.

| | | Waits | What it means |
|---|---|---|---|
| **32** | Reverse power | 6 s | The bus is driving your engine as a motor |
| **51** | Overcurrent | inverse | Stator current above 115 %, the harder over the faster |
| **81** | Over/under frequency | 4 s | The system went outside the limits you may run tied to |
| **40** | Loss of field | 5 s | Excitation collapsed while carrying load |
| **87** | Differential | none | A fault inside the machine or its leads |

While a relay is timing out its disc creeps up the window, so a trip you are
about to take is visible before you take it — and, like a real induction disc,
it runs back down again when the condition clears rather than forgetting where
it was.

**The breaker is interlocked against a dropped target.** You cannot put the unit
back on the bars until you have gone to the panel and reset it by hand, which
means you cannot carry on without first finding out what put you off. Tap the
window to reset it.

None of this ends your shift. A relay trip is recoverable: find out why, reset,
re-synchronise. What it costs is the kilowatt-hours you were not exporting while
you sorted it out, and the dispatcher is counting those.

## The six cylinders

The engine is six pots firing 1-5-3-6-2-4, and each one is modelled on its own.
Its own igniter with its own deposit, its own feed off the lubricator, its own
oil film, its own exhaust temperature, its own share of the torque. No two of
them breathe quite alike, so the pyrometer bank is a ragged skyline even when
everything is right, and you learn which of yours runs hot.

That makes a rough engine **diagnosable**. One pot quitting costs you a sixth of
the power and shows up three ways: the beat changes, the annunciator lights
MISFIRE, and that column on the pyrometer goes cold while the other five stay
hot. Six cut-out switches let you short an igniter out deliberately, which is
how you prove which one it is — and how you nurse a bad cylinder rather than let
it hammer the bottom end.

The six sight feeds are the other half of it. The master handwheel on the
control deck sets the pump stroke; each sight feed meters that pot's share.
Shut one and only that liner runs dry, only that piston picks up, and only that
column of the gauge glass runs dark. Half is the nominal setting the day man
leaves them at.

## The tanks

Everything the engine consumes comes from a vessel with a gauge glass on it.

- **Fuel** is lifted from the buried tank outside to a gravity day tank in the
  roof by a transfer pump on the main bus, and falls from there to the
  carburettor. The day tank holds about a quarter of an hour at full load, so
  transferring is a job you keep coming back to. Below about thirty-five litres
  the head goes off and the charge leans out; the engine dies lean, not rich.
  Leave the pump running with the tank full and it goes out of the overflow onto
  the floor, and the inspector counts those litres.
- **Jacket water** circulates from a header tank that boils some away every hour
  the engine is hot. Over a full shift it wants topping up about once. Let the
  header run down and the circulating pump has nothing to circulate, whatever
  the gate valve says. The outlet thermometer reads hotter than the iron by
  whatever the water is carrying away, so a rising outlet with a steady jacket
  means the flow is falling.
- **Lubricating oil** goes up the bores and does not come back. There is a drum
  and a hand pump.

**The fuel cock is also the stop.** There is no other way to shut this engine
down deliberately: shut the cock and it runs itself out. A flywheel this size
takes about a minute and a half to come to rest.

## The supply selector

One rotary decides what holds the emergency line up:

```
GRID --- GEN --- OFF --- EMG
  |         |             |
the       the           the
system    terminals     battery
```

- **GRID** — off the starting transformer's own secondary, so it is the system
  and nothing else. Full and steady at any speed, so it will start a stone cold
  engine. But it fades as the grid volts sag, which means the ignition and the
  field go weak exactly when the system is in trouble and you most need the
  machine — and it goes away completely the moment you open the starting
  transformer breaker, which you have to do before you excite.
- **GEN** — the output of the emergency transformer, off the generator
  terminals. Alive whenever *anything* is holding that bar up: the system while
  the starting transformer is in, and your own machine once it is out. Islanded
  it is the right place to run, because nothing outside the station can take it
  away, and the circle closes on itself — the field is one of the loads on the
  line that transformer is holding up.
- **OFF** — dead.
- **EMG** — the battery, through the battery breaker. A fat spark at cranking
  speed that fades as the revolutions rise, and the only position that will turn
  the starting motor. It flattens the cells, which are put back by the emergency
  transformer — and that wants the terminals live, from either end.

The layout is the point: `EMG` is where you start and `GEN` is where you run,
and getting between them means passing through `OFF` with no ignition at all —
and with no excitation either. Do it briskly. Dawdle and the field decays past
the point where the emergency transformer has anything to work on, so landing on
`GEN` gives you nothing and you are back to the battery.

## Getting the engine lit, cold

The day man left the board ready: the starting transformer in so the station is
alive, a fast idle on the throttle, a rich needle, a retarded spark, the water
gate cracked and the lubricator feeding. So the short
version is **selector to `EMG`, hold the starter until it catches, let go.**

The longer version, and what each of those settings is for:

1. **Mixture rich.** On cold iron most of the gasoline condenses on the port
   walls and never burns, so what reaches the cylinder is far leaner than what
   you metered. Lean it out as the jacket warms or it will foul the plugs.
2. **Spark retarded.** A cold engine at cranking speed wants very little lead.
   Advance it as the revolutions rise.
3. **A fast idle on the throttle.** A cold engine is down on power and this one
   has a great deal of its own friction to overcome. Bring the throttle back as
   it warms, or it will run away — there is no governor to catch it.
4. **Prime it** if it is being stubborn: one or two squirts. More than three and
   you will flood it and have to clear it.
5. **The relief cock** takes the compression off so the starter spins the engine
   up faster. Shut it again before it can fire — with the cock open no charge
   will light at all.
6. **Do not sit on the starter.** The cells also carry the ignition, the field
   and the emergency pump, so emergency supply is a clock.
7. **Open the starting transformer breaker, then bring the field up.** In that
   order, always. With the starting transformer in, the terminals are tied to
   the system through it and exciting the machine throws it off. Once it is open
   the station is islanded, and the field is what holds everything up: the main
   bus and its pumps, the emergency transformer, and the cells. Then sweep the
   selector across to `GEN`.

Then let the jacket come up to about 78 °C before you ask much of it.

## Getting on the bus

The three lamps and the synchroscope are wired across the open breaker contacts.

1. **Match the cycles.** Trim the throttle until the machine is turning a whisker
   faster than the bus, so the synchroscope creeps slowly towards `FAST`.
2. **Match the volts.** Bring the field rheostat up until the machine voltmeter
   reads what the bus voltmeter reads.
3. **Wait for the lamps to go dark.** They are brightest when the machines are in
   opposition and dark at coincidence.
4. **Close the breaker** as the pointer comes up to the mark at twelve o'clock.

Close it out of phase and the shock is calculated from the phase error, the slip
and the voltage mismatch. A little out and you will feel it and mark the
coupling. Far out and the coupling shears and takes the crankshaft with it.

Closing onto a live bus with the machine stopped is a short circuit in all but
name. The board will let you.

## Once you are paralleled

The machine is now locked to the bus, and the controls change meaning:

- **Throttle** no longer sets speed. It sets the **rotor angle**, and therefore
  the **kilowatts** you are pushing out. Too little and the bus drives your
  engine as a motor — that is the reverse power alarm.
- **Field rheostat** no longer sets volts. It sets the internal EMF, and
  therefore the **reactive load**. Too little field and the pull-out limit falls
  until the rotor slips a pole, which is spectacular and final.

The dispatcher's order steps every ninety seconds and the header shows what you
are asked for against what you are actually putting on the bars. Being off order
costs you.

The system is not perfectly steady either. It wanders about 60 cycles, and now
and then something large elsewhere trips and knocks the frequency down. When
that happens your machine leans in on its own — the rotor angle opens and your
output jumps — and you have to trim back. Let the frequency go far enough
outside limits while you are tied on and the system protection sheds you.

## Things that will end your shift

Seized main bearing · thrown connecting rod · piston burned through by
detonation · **a scored liner from a sight feed left shut** · seizure from a
boiled-dry jacket (including one caused by leaving both pumps switched out, or
by letting the header tank run down) · burst flywheel from throwing the load off
at full throttle · sheared coupling from closing out of phase · pole slip from
too little field · **a flashed-over field winding from throwing the field switch
in hot once too often** · a flat battery with a machine that cannot excite
itself at rest.

A relay trip is **not** on that list. Being thrown off the bars by the protection
is a bad half hour, not the end of the shift.

**Throwing the breaker open at full load is the fastest way to destroy the
engine.** There is no governor to catch it. Shut the throttle first.

**So does the starting transformer.** Open its breaker before the field goes up,
not after. Leaving it in costs you a trip and a re-close, and there is no way to
synchronise properly with the machine already tied to the system through it.

**The field switch has an order of operations.** Opening it is always safe: the
discharge resistor beside it takes the field current away gently. Closing it is
not. Throw it in with the rheostat anywhere but at the bottom and the whole
field goes on at once; the insulation stands that a few times and then it does
not. Rheostat down, switch in, rheostat up.

## Building

Requires the Android SDK (compileSdk 35) and a JDK 17 or newer.

```sh
./gradlew :app:assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # simulation, rendering and touch tests
```

## How it is put together

```
sim/     Plant, Engine, Cylinder, Generator, Grid, Service, Protection,
         Auxiliaries — plain Kotlin, no Android imports
ui/      Theme, Gauges, Widgets, Mimic, Layout, PanelRenderer — all drawn on a Canvas
game/    GameView — the loop and the multi-touch handling
audio/   EngineAudio — synthesised at runtime, no sample files
```

The simulation is deliberately free of Android dependencies so it can be tested
on the JVM. The alternator uses the classic power-angle model with damper
windings, which is why the throttle moves watts and the field moves vars, and
why pull-out and pole slip fall out of the physics rather than being special
cased.

The board is drawn in a virtual space 1080 wide and as tall as the phone's
aspect ratio calls for, so it fills the screen edge to edge from 16:9 to 21:9
without letterboxing. Static ironwork is cached to a bitmap and rebuilt only
when the light in the room changes; only the needles, lamps and handles are
redrawn each frame.

Sound is generated a chunk at a time straight into an `AudioTrack`. The exhaust
beat is driven by the engine model's own firing events, so misfires and
backfires are audible, and detonation can be heard before it is fatal.

## Tests

`SimTest` drives the plant the way a competent operator would and checks the
physics behaves: the cold-start ritual, the ignition changeover, paralleling
cleanly and disastrously, load-rejection overspeed, the neglect failures, every
relay in the panel and its interlock, a cut-out cylinder costing its own sixth
and no more, one shut sight feed scoring one liner and no other, and each tank
running down and being put back.

`RenderTest` draws nine states of the board to PNG under Robolectric's native
graphics, so the panel can be inspected without a device. Output lands in
`app/build/screens`.

`StressTest` goes after the crash rather than the physics: it thumps the board
at random across every deck for six thousand frames checking nothing throws and
no number has gone to NaN, draws every awkward state (wrecked, every switch out,
every fuse gone, every tank empty), and holds the two threading contracts that
used to break the game — the cached background must not be recycled while the
loop is drawing it, and the loop must turn only while there is both a surface
and a resumed activity.

`ControlsTouchTest` dispatches real touch events at every hand control and
checks each one moves the thing it is connected to — including working two
levers at once with two fingers, and that each deck only answers while it is
the one showing.
