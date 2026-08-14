# Dynamo Room

An Android game about running a late-1920s gasoline-engine electric light plant
by hand. There is no governor, no voltage regulator, no automatic synchroniser
and no tutorial. There is an engine, a switchboard, and a town that expects its
lights to work.

You are the night operator at the Millbrook Electric Light & Power Company. A
two-cylinder horizontal gasoline engine drives a 12-pole, 2300 volt, 60 cycle
alternator. The town bus is shared with the little Willow Creek hydro station,
which can carry 56 kilowatts and not one watt more. Demand steps to a new figure
every ninety seconds, all night.

## The controls

**The five mains**

| Control | What it does |
|---|---|
| Supply selector | `GRID · GEN · OFF · EMG`, in that physical order |
| Throttle | The only speed control there is, and therefore the frequency control |
| Spark lever | −5° to +38° before top dead centre |
| Mixture | 17.5:1 lean to 9.5:1 rich, as supplied to the intake |
| Field rheostat | Excitation: terminal volts off the bus, reactive load on it |

**Switchgear**: main breaker, field switch, and four feeder knife switches with
cartridge fuses — Main Street lighting, Mill No. 2 motors, the ice house, and
the street railway.

**Starting gear**: relief cock, primer, starting crank, and the electric starting
motor (wired through the `EMG` position only).

**Auxiliaries**: cooling water gate and the mechanical lubricator.

## The two decks

The instrument board across the top is always in view: synchroscope, cycles,
the three synchronising lamps, machine and bus voltmeters, wattmeter, ammeter.
Below it the panel switches between two decks, which is the walk an operator
makes between the machine and the board:

- **CONTROL** — engine speed, oil and jacket gauges, the five mains, the
  starting gear, and the water and oil handwheels.
- **ELECTRICAL** — the station single line, and under it the switchboard
  carrying the main breaker, the field switch and the four feeders.

A red pip appears on whichever tab you are not looking at when something over
there wants attention. The annunciator strip along the foot is always live.

## The single line

The mimic diagram on the electrical deck is drawn the way the board really is
wired, and it shows what is actually connected to what. Live conductors run
bright with current beads sliding along them; dead ones go grey; open contacts
show as a blade swung clear of its jaws.

```
   WILLOW CREEK
        |
  ======+=================== TOWN BUS 2300 V ==================
        |            |            |     |     |     |
    MAIN TX      STARTING TX      the four feeders, each
        |            |            through its switch and fuse
     BREAKER         |
        |            |
      [ GEN ]        |
        |            |
        +---(GEN)----+---(GRID)---- STATION SERVICE ----(EMG)--- BATTERY
```

Only one of the three taps into the station service bus is made at a time, and
the selector is what makes it. Watch the battery branch: it runs one way when
you are drawing off `EMG` and the other way when the charging set is putting the
cells back.

## The supply selector

One rotary decides where the ignition and the panel lamps are fed from:

```
GRID --- GEN --- OFF --- EMG
  |       |               |
  bus    the machine     the battery
```

- **GRID** — station service tapped off the town bus through the starting
  transformer. Full and steady at any speed, so it will start a stone cold
  engine. But it fades as the bus volts sag, which means the ignition goes weak
  exactly when the system is in trouble and you most need the engine.
- **GEN** — the machine's own shaft-driven exciter. Nothing at rest,
  strengthening with speed. It cannot start the engine, and it is the right
  place to run it, because nothing outside the station can take it away.
- **OFF** — dead.
- **EMG** — the emergency battery. A fat spark at cranking speed that fades as
  the revolutions rise, and the only position that will turn the starting motor.
  It also flattens the cells, which are only put back while the service bus is
  being fed from `GRID` or `GEN`.

The layout is the point: `EMG` is where you start and `GEN` is where you run,
and getting between them means passing through `OFF` with no ignition at all.
Do it briskly. Dawdle long enough and the engine coasts below the speed the
exciter needs, so landing on `GEN` cannot relight it and you are back to the
battery.

## Getting the engine lit, cold

1. Water gate barely cracked. A cold engine wants to warm up.
2. Lubricator feeding — about a third open to start.
3. Mixture **rich**. On cold iron most of the gasoline condenses on the port
   walls and never burns, so what reaches the cylinder is far leaner than what
   you metered.
4. Spark lever **fully retarded**. This matters: a charge firing before top dead
   centre while you are on the crank handle will kick back and break your wrist,
   and that ends the shift.
5. Prime it — one or two squirts. More than three and you will flood it.
6. Open the relief cock. You cannot hand-crank against full compression.
7. Selector to `EMG`, then either hold the starter button or swipe round the
   crank. (`GRID` will fire the plugs too, but only the battery turns the
   starting motor.)
8. Once it is spinning, shut the relief cock and let it fire.

Then bring the throttle up, lean the mixture out and advance the spark as the
revolutions rise, and let the jacket come up to about 78 °C before you ask much
of it. When it is running properly, sweep the selector across to `GEN`.

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

Demand steps every ninety seconds. Willow Creek leans in as the bus falls but is
against its stops at 56 kilowatts, after which the cycles are yours to hold. If
you cannot carry the town, pull a feeder and shed it deliberately. If you
over-generate, the frequency runs away just as surely.

## Things that will end your shift

Kickback on the crank · seized main bearing · thrown connecting rod · piston
burned through by detonation · seizure from a boiled-dry jacket · burst flywheel
from throwing the load off at full throttle · sheared coupling from closing out
of phase · pole slip from too little field · a blacked-out town · a flat battery
with an exciter that will not fire at rest.

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
sim/     Plant, Engine, Generator, Grid — plain Kotlin, no Android imports
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

`RenderTest` draws five states of the board to PNG under Robolectric's native
graphics, so the panel can be inspected without a device. Output lands in
`app/build/screens`.

`ControlsTouchTest` dispatches real touch events at every hand control and
checks each one moves the thing it is connected to — including working two
levers at once with two fingers, and that each deck only answers while it is
the one showing.
