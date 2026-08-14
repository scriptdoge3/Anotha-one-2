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
supplies: the ignition, the cooling water pump, the battery charger and the
house lighting all hang off a station service bus, and keeping that bus alive is
what keeps the engine alive.

## The controls

**The five mains**

| Control | What it does |
|---|---|
| Supply selector | `GRID · GEN · OFF · EMG`, in that physical order |
| Throttle | The only speed control there is, and therefore the frequency control |
| Spark lever | −5° to +38° before top dead centre |
| Mixture | 17.5:1 lean to 9.5:1 rich, as supplied to the intake |
| Field rheostat | Excitation: terminal volts off the bus, reactive load on it |

**Switchboard (internal supplies)**: the unit breaker out to the grid, the field
switch, and four knife switches with cartridge fuses feeding the plant itself —
ignition (0.4 kW), cooling water pump (3.4 kW), battery charger (2.1 kW) and
house lights (1.3 kW).

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
- **ELECTRICAL** — the station single line, and under it the switchboard
  carrying the unit breaker, the field switch and the four internal supplies.

A red pip appears on whichever tab you are not looking at when something over
there wants attention. The annunciator strip along the foot is always live.

## The single line

The mimic diagram on the electrical deck is drawn the way the board really is
wired, and it shows what is actually connected to what. Live conductors run
bright with current beads sliding along them; dead ones go grey; open contacts
show as a blade swung clear of its jaws.

```
   INTERCONNECTION
        |
  ======+============= GRID 2300 V =========================
        |                        |
    MAIN TX                 STARTING TX
        |                        |
    UNIT BKR                     |
        |                        |
      [ GEN ]                    |
        |                        |
        +---(GEN)----------------+---(GRID)-----+------(EMG)--- BATTERY
                                                |
                    ===== STATION SERVICE =====
                       |      |      |      |
                      IGN   PUMP   CHGR   LIGHT
                     0.6kW 14.0kW  4.5kW  3.0kW
```

Only one of the three taps into the station service bus is made at a time, and
the selector is what makes it. Each internal load has its own switch and fuse on
the board. Watch the battery branch: it runs one way when you are drawing off
`EMG` and the other way when the charging set is putting the cells back.

**The bus has a limit.** The grid and the generator will each carry 60 kW, which
is more than the whole board asks for. The battery will carry 18 kW, which is
not — the water pump alone is 14 kW. On emergency supply you have to decide what matters: the charger cannot put
anything back while the battery is the thing feeding the bus, so switch it out.
Overload the bus far enough and the volts sag until loads drop out, and then a
fuse goes.

**The cooling water pump is an electric machine on that bus.** The gate valve on
the control deck meters the flow, but if the pump is not running the gate does
nothing at all and the engine will cook with the valve wide open.

## The supply selector

One rotary decides where the ignition and the panel lamps are fed from:

```
GRID --- GEN --- OFF --- EMG
  |       |               |
  bus    the machine     the battery
```

- **GRID** — station service tapped off the grid through the starting
  transformer. Full and steady at any speed, so it will start a stone cold
  engine. But it fades as the grid volts sag, which means the ignition goes weak
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
6. **Do not sit on the starter.** The cells also carry the ignition and the
   14 kW cooling water pump, so emergency supply is a clock. Get it lit, let go,
   and sweep the selector across to `GEN`.

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
caused by leaving the water pump switched out) · burst flywheel from throwing
the load off at full throttle · sheared coupling from closing out of phase ·
pole slip from too little field · thrown off the system by the protection · a
flat battery with an exciter that will not fire at rest.

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

`RenderTest` draws five states of the board to PNG under Robolectric's native
graphics, so the panel can be inspected without a device. Output lands in
`app/build/screens`.

`ControlsTouchTest` dispatches real touch events at every hand control and
checks each one moves the thing it is connected to — including working two
levers at once with two fingers, and that each deck only answers while it is
the one showing.
