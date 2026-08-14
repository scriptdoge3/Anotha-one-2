# Dynamo Room

An Android game about running a late-1920s gasoline-engine electric light plant
by hand. There is no governor, no voltage regulator, no automatic synchroniser
and no tutorial. There is an engine, a switchboard, and a dispatcher who expects
his kilowatts on time.

You are the night operator at the Millbrook Light & Power Company. A horizontal
gasoline engine drives a 12-pole, 2300 volt, 60 cycle alternator rated **500
kilowatts**, and that set is tied into a full interconnection through a main
transformer and a unit breaker.

The grid is large. Even 500 kilowatts is small against it, so you do not set its
frequency — you follow it. What you do control is how much you put onto
the bars, and the dispatcher hands you a new load order every ninety seconds.

The switchboard is not for the grid. It is for the plant's own internal
supplies, and there are two of them. The **main bus** carries the regular
running gear — the control supply, the circulating pump, the oil pump, the house
lights — and hangs off the generator terminals through the station transformer,
so it is dead until the machine is turning and excited. The **emergency line**
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

| Main bus | | Emergency line | |
|---|---|---|---|
| Control supply | 1.2 kW | Emergency TX breaker | — |
| Circulating pump | 14.0 kW | Battery breaker | — |
| Oil pump | 5.5 kW | Ignition | 0.6 kW |
| House lights | 3.0 kW | Excitation | 2.2 kW |
| | | Emergency pump | 4.0 kW |
| | | Emergency lights | 1.2 kW |

**Starting gear**: relief cock, primer, and the electric starting motor, which is
wired through the `EMG` position only.

**Auxiliaries**: cooling water gate and the mechanical lubricator.

## The two decks

The instrument board across the top is always in view: synchroscope, cycles,
the three synchronising lamps, machine and bus voltmeters, wattmeter, ammeter.
Below it the panel switches between two decks, which is the walk an operator
makes between the machine and the board:

- **CONTROL** — three panels. **ENGINE** carries the tachometer, the supply
  selector, throttle, spark, mixture and the starting gear. **OIL AND WATER**
  carries the oil pressure and jacket gauges with the lubricator and water gate
  handwheels. **GENERATOR** carries the field rheostat with field and reactive
  meters and the power factor.
- **ELECTRICAL** — the station single line, and under it the switchboard: the
  unit breaker, the main bus row, and the emergency row with its two breakers.

A red pip appears on whichever tab you are not looking at when something over
there wants attention. The annunciator strip along the foot is always live.

## The single line

The mimic diagram on the electrical deck is drawn the way the board really is
wired, and it shows what is actually connected to what. Live conductors run
bright with current beads sliding along them; dead ones go grey; open contacts
show as a blade swung clear of its jaws.

```
  ================= GRID 2300 V =========================
        |                                            |
   UNIT BREAKER                                 STARTING TX
        |                                            |
     MAIN TX                                         |
        |                                            |
  ======+========= GENERATOR TERMINALS ==========     |
        |        |                    |              |
     [ GEN ]  STATION TX         EMG TX BREAKER       |
                 |                    |              |
  ===== MAIN BUS =====            EMERGENCY TX        |
    |    |    |    |                  |              |
  CTRL CIRC  OIL LIGHT                +--- BATTERY   |
   1.2 14.0  5.5  3.0                 |       |      |
                                    (GEN)  BATT BKR  |
                                      |       |      |
                                      |     (EMG)  (GRID)
                                      |       |      |
  ================= EMERGENCY LINE ====+=======+======+==
    |      |       |       |
   IGN    EXC   E.PUMP   E.LT
   0.6    2.2     4.0     1.2
```

The mimic is coloured the way a real control room panel is: **red** for the
high tension side, **orange** for generator voltage and the main bus, **green**
for the emergency circuit. Every switching device carries a lamp, green when it
is made and red when it is open.

**The two internal supplies never touch.** They are separate all the way back
to the generator terminals, each with its own transformer, and nothing ties one
to the other. Losing the main bus costs you the pumps and the lights; losing the
emergency line stops the engine.

**The main bus** is fed off the generator terminals through the station
transformer, and nothing else feeds it. Until the machine is turning and excited
there is no control supply, no circulating pump, no oil pump and no house
lights. Getting the field up is therefore part of starting, not part of
synchronising.

**The emergency circuit comes straight off the generator terminals too**, on
its own breaker and its own transformer. That transformer's output does two
things: it charges the battery, and it is what the `GEN` position of the
selector puts on the emergency line. The battery floats across the same output
and goes out to the line through the battery breaker.

That is the only road the charge takes back to the cells, so with the emergency
transformer breaker open the battery is islanded and will only ever run down —
and an unexcited machine gives its transformer nothing to work on, so the field
you need for charging comes off the line you are charging. Open the battery
breaker and the cells are off the line altogether: no battery ignition, and no
starting motor.

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

## The supply selector

One rotary decides what holds the emergency line up:

```
GRID --- GEN --- OFF --- EMG
  |       |               |
  bus    the machine     the battery
```

- **GRID** — tapped off the grid bus through the starting transformer. Full and
  steady at any speed, so it will start a stone cold engine. But it fades as the
  grid volts sag, which means the ignition and the field go weak exactly when
  the system is in trouble and you most need the machine.
- **GEN** — the output of the emergency transformer: the machine carrying its
  own emergency line. Dead until the machine is excited, so it cannot start the
  engine, and it is the right place to run it because nothing outside the
  station can take it away. The circle closes on itself: the field is one of the
  loads on the line that transformer is holding up.
- **OFF** — dead.
- **EMG** — the battery, through the battery breaker. A fat spark at cranking
  speed that fades as the revolutions rise, and the only position that will turn
  the starting motor. It also flattens the cells, which are only put back by the
  emergency transformer, and that wants an excited machine.

The layout is the point: `EMG` is where you start and `GEN` is where you run,
and getting between them means passing through `OFF` with no ignition at all —
and with no excitation either. Do it briskly. Dawdle and the field decays past
the point where the emergency transformer has anything to work on, so landing on
`GEN` gives you nothing and you are back to the battery.

## Getting the engine lit, cold

The day man left the board ready: a fast idle on the throttle, a rich needle, a
retarded spark, the water gate cracked and the lubricator feeding. So the short
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
7. **Bring the field up once it is lit.** Until the machine is making volts
   there is no main bus — no circulating pump, no oil pump — and no emergency
   transformer either, so nothing for the `GEN` tap to carry and nothing putting
   the cells back. Then sweep the selector across to `GEN`.

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

Seized main bearing · thrown connecting rod · piston
burned through by detonation · seizure from a boiled-dry jacket (including one
caused by leaving both pumps switched out) · burst flywheel from throwing
the load off at full throttle · sheared coupling from closing out of phase ·
pole slip from too little field · thrown off the system by the protection · a
flat battery with a machine that cannot excite itself at rest.

**Throwing the breaker open at full load is the fastest way to destroy the
engine.** There is no governor to catch it. Shut the throttle first.

## Building

Requires the Android SDK (compileSdk 35) and a JDK 17 or newer.

```sh
./gradlew :app:assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # simulation, rendering and touch tests
```

## How it is put together

```
sim/     Plant, Engine, Generator, Grid, Service — plain Kotlin, no Android imports
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
cleanly and disastrously, load-rejection overspeed, and the neglect failures.

`RenderTest` draws seven states of the board to PNG under Robolectric's native
graphics, so the panel can be inspected without a device. Output lands in
`app/build/screens`.

`ControlsTouchTest` dispatches real touch events at every hand control and
checks each one moves the thing it is connected to — including working two
levers at once with two fingers, and that each deck only answers while it is
the one showing.
