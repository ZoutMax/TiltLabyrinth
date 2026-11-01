#!/usr/bin/env python3
"""Synthesizes the sound effects into app/src/main/res/raw/ (pure Python, 44100 Hz, 16-bit mono).

Run from the repo root:  python3 tools/gen_sounds.py
"""
import math
import os
import random
import struct
import wave

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
random.seed(3)


def write(name, samples):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples))
    print(f"{name:12s} {len(samples) / SR:5.2f}s")


def lowpass(samples, alpha):
    out = []; y = 0.0
    for s in samples:
        y += alpha * (s - y); out.append(y)
    return out


def seamless(samples, n, xf):
    """A loop of n samples: the tail beyond n is blended over the first xf samples so the join is silent-free."""
    out = samples[:n]
    for i in range(xf):
        k = i / xf
        out[i] = out[i] * k + samples[n + i] * (1 - k)
    return out


def highpass(samples, alpha):
    out = []; y = 0.0; prev = 0.0
    for s in samples:
        y = alpha * (y + s - prev); prev = s; out.append(y)
    return out


def main():
    # Rolling steel pinball, in two layers the game mixes by speed:
    #  - roll_loop: the deep body rumble of a heavy ball on the playfield
    #  - roll_grain: the bright grain of steel on the surface, thousands of tiny impacts a second,
    #    with the faint ring of the steel itself. Both loop with a crossfade so nothing pulses.
    n = 2 * SR; xf = int(0.3 * SR)
    noise = [random.uniform(-1, 1) for _ in range(n + xf)]
    deep = lowpass(lowpass(lowpass(noise, 0.025), 0.025), 0.15)
    body = lowpass(highpass(noise, 0.95), 0.06)
    roll = [d * 6.0 + b * 0.5 for d, b in zip(deep, body)]
    roll = seamless(roll, n, xf)
    peak = max(abs(s) for s in roll)
    write("roll_loop", [s / peak * 0.8 for s in roll])

    grain = [0.0] * (n + xf)
    i = 0
    while i < n + xf:
        i += int(random.expovariate(1800) * SR) + 1  # micro-impacts
        a = random.uniform(0.2, 1.0) ** 2
        for j in range(min(int(0.0012 * SR), n + xf - i)):
            grain[i + j] += a * random.uniform(-1, 1) * math.exp(-j / (0.0003 * SR))
    grain = lowpass(highpass(grain, 0.9), 0.45)  # bandpass: the metallic mid-highs
    ring = [(math.sin(2 * math.pi * 3150 * k / SR) * 0.5 + math.sin(2 * math.pi * 5230 * k / SR) * 0.3) * 0.06 * (0.6 + 0.4 * math.sin(2 * math.pi * 9 * k / n)) for k in range(n + xf)]
    grain = [g * 1.4 + r for g, r in zip(grain, ring)]
    grain = seamless(grain, n, xf)
    peak = max(abs(s) for s in grain)
    write("roll_grain", [s / peak * 0.7 for s in grain])

    # Knock: a short wooden tap, decaying sine with a noise click
    d = 0.09; m = int(d * SR)
    knock = []
    for i in range(m):
        t = i / SR
        env = math.exp(-t * 45)
        knock.append((math.sin(2 * math.pi * 520 * t) * 0.7 + math.sin(2 * math.pi * 1240 * t) * 0.2 + random.uniform(-1, 1) * 0.25 * math.exp(-t * 200)) * env * 0.9)
    write("knock", knock)

    # Finish: ball dropping into the hole, then a Roman trumpet fanfare, two brassy notes "ta-DAAA"
    def brass(f0, t, dur):
        if t < 0 or t > dur:
            return 0.0
        env = min(1.0, t / 0.035) * (1.0 if t < dur - 0.12 else max(0.0, (dur - t) / 0.12))
        f = f0 * (1 - 0.04 * math.exp(-t / 0.04)) * (1 + 0.004 * math.sin(2 * math.pi * 5.5 * t) * min(1.0, t / 0.25))
        bright = 0.45 + 0.55 * min(1.0, t / 0.12)  # the horn opens up as the note blooms
        v = 0.0
        for k in range(1, 11):
            v += math.sin(2 * math.pi * k * f * t) / (k ** 0.9) * (bright ** (k - 1))
        return v * env * 0.07  # the horns play softly, well under the effects

    d = 1.6; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        thud = math.sin(2 * math.pi * 140 * t) * math.exp(-t * 18) * 0.8
        horn = brass(392.0, t - 0.15, 0.28) + brass(523.25, t - 0.43, 1.1)
        out.append(thud + horn)
    write("finish", out)

    # Fall: a drop with a descending tone and a hollow thud
    d = 0.9; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        tone = math.sin(2 * math.pi * (600 - 500 * t / d) * t) * 0.35 * (1 - t / d)
        thud = math.sin(2 * math.pi * 90 * t) * math.exp(-max(0.0, t - 0.5) * 20) * (0.0 if t < 0.5 else 0.8)
        out.append(tone + thud)
    write("fall", out)

    # Magnet hum: mains buzz with harmonics, a slow throb; whole cycles per second so it loops clean
    n = SR
    hum = []
    for i in range(n):
        t = i / SR
        throb = 0.75 + 0.25 * math.sin(2 * math.pi * 6 * t)
        # Weighted toward the harmonics so phone speakers, which lose the 60 Hz, still carry it loud
        v = (math.sin(2 * math.pi * 60 * t) * 0.3 + math.sin(2 * math.pi * 120 * t) * 0.38
             + math.sin(2 * math.pi * 180 * t) * 0.3 + math.sin(2 * math.pi * 240 * t) * 0.22 + math.sin(2 * math.pi * 360 * t) * 0.12)
        hum.append(math.tanh(v * throb * 2.6) * 0.98)  # driven hard into saturation: a loud, dense electric buzz
    write("magnet_loop", hum)

    # Wind: rushing filtered noise with a gusty swell (periodic over the loop), crossfaded join
    n = SR; xf = int(0.1 * SR)
    noise = [random.uniform(-1, 1) for _ in range(n + xf)]
    wind = lowpass(lowpass(noise, 0.18), 0.5)
    wind = [s * 2.6 * (0.6 + 0.4 * math.sin(2 * math.pi * 2 * i / n)) for i, s in enumerate(wind)]
    write("wind_loop", seamless(wind, n, xf))

    # Radioactive: Geiger clicks over a faint hiss, crossfaded join
    geiger = [random.uniform(-1, 1) * 0.04 for _ in range(n + xf)]
    geiger = lowpass(geiger, 0.6)
    i = 0
    while i < n + xf:
        i += int(random.expovariate(28) * SR) + 1
        for j in range(min(int(0.004 * SR), n + xf - i)):
            geiger[i + j] += (random.uniform(-1, 1) * 0.9 + math.sin(2 * math.pi * 3000 * j / SR) * 0.5) * math.exp(-j / (0.0012 * SR))
    write("geiger_loop", seamless(geiger, n, xf))

    # Lid: the dull clack of a trap lid snapping open or shut
    d = 0.07; m = int(d * SR)
    lid = []
    for i in range(m):
        t = i / SR
        env = math.exp(-t * 60)
        lid.append((math.sin(2 * math.pi * 310 * t) * 0.6 + math.sin(2 * math.pi * 740 * t) * 0.2 + random.uniform(-1, 1) * 0.3 * math.exp(-t * 250)) * env * 0.9)
    write("lid", lid)

    # Magnet switching on: a solenoid clunk, then the buzz rising into the hum
    d = 0.35; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        clunk = (math.sin(2 * math.pi * 150 * t) * 0.7 + random.uniform(-1, 1) * 0.3 * math.exp(-t * 300)) * math.exp(-t * 40)
        rise = 0.0
        if t > 0.05:
            tt = t - 0.05
            f = 40 + 20 * min(1.0, tt / 0.25)
            rise = (math.sin(2 * math.pi * f * tt) * 0.5 + math.sin(2 * math.pi * 2 * f * tt) * 0.3) * min(1.0, tt / 0.1) * 0.6 * (1 - max(0.0, (t - 0.25) / 0.1))
        out.append(math.tanh((clunk + rise) * 2.2) * 0.98)
    write("magnet_on", out)

    # Fan starting: noise swelling as the blades spin up, pitch climbing
    d = 0.5; m = int(d * SR)
    noise = [random.uniform(-1, 1) for _ in range(m)]
    sw = lowpass(lowpass(noise, 0.15), 0.5)
    out = []
    for i in range(m):
        t = i / SR
        k = min(1.0, t / 0.35)
        whir = math.sin(2 * math.pi * (60 + 140 * k) * t) * 0.25 * k
        out.append(sw[i] * 2.4 * k * (1 - max(0.0, (t - 0.4) / 0.1)) + whir * (1 - max(0.0, (t - 0.4) / 0.1)))
    write("wind_on", out)

    # Radioactive leak starting: a hiss of escaping gas with a burst of clicks
    d = 0.45; m = int(d * SR)
    noise = [random.uniform(-1, 1) for _ in range(m)]
    hiss = lowpass(noise, 0.7)
    out = []
    for i in range(m):
        t = i / SR
        env = min(1.0, t / 0.03) * math.exp(-t * 5)
        out.append(hiss[i] * 0.5 * env)
    i = 0
    while i < m:
        i += int(random.expovariate(60) * SR) + 1
        for j in range(min(int(0.004 * SR), m - i)):
            out[i + j] += (random.uniform(-1, 1) * 0.9) * math.exp(-j / (0.0012 * SR)) * math.exp(-i / SR * 6)
    write("geiger_on", out)

    # Speed trap charged: an electric crackle, a buzz with random sparks, crossfaded loop
    n = SR; xf = int(0.1 * SR)
    out = []
    for i in range(n + xf):
        t = i / SR
        buzz = (math.sin(2 * math.pi * 110 * t) * 0.35 + math.sin(2 * math.pi * 220 * t) * 0.2 + math.sin(2 * math.pi * 1760 * t) * 0.05) * (0.8 + 0.2 * math.sin(2 * math.pi * 7 * i / n))
        out.append(buzz * 0.6)
    i = 0
    while i < n + xf:
        i += int(random.expovariate(40) * SR) + 1
        for j in range(min(int(0.003 * SR), n + xf - i)):
            out[i + j] += random.uniform(-1, 1) * 0.7 * math.exp(-j / (0.0008 * SR))
    write("speed_loop", seamless(out, n, xf))

    # Speed trap switching on: a charging whine climbing and snapping
    d = 0.4; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        f = 200 + 1800 * (t / 0.3) ** 2 if t < 0.3 else 2000
        env = min(1.0, t / 0.02) * (1.0 if t < 0.3 else max(0.0, 1 - (t - 0.3) / 0.1))
        out.append(math.sin(2 * math.pi * f * t) * 0.4 * env + (random.uniform(-1, 1) * 0.5 * math.exp(-(t - 0.3) * 60) if t >= 0.3 else 0.0))
    write("speed_on", out)

    # Zap: the ball taking the charge, a bright electric snap with a short ring
    d = 0.3; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        snap = random.uniform(-1, 1) * 0.9 * math.exp(-t * 90)
        ring = math.sin(2 * math.pi * (1400 - 600 * t) * t) * 0.5 * math.exp(-t * 14)
        out.append(snap + ring)
    write("zap", out)

    # Trapdoor arming: a slow wooden creak (a low tone whose pitch wavers, with rough grain)
    d = 0.5; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        f = 180 + 60 * math.sin(2 * math.pi * 3 * t) + 40 * t
        env = min(1.0, t / 0.05) * (1 - t / d)
        out.append((math.sin(2 * math.pi * f * t) * 0.5 + math.sin(2 * math.pi * 2 * f * t) * 0.25 + random.uniform(-1, 1) * 0.15) * env * 0.5)
    write("trapdoor_on", out)

    # Trapdoor slamming open: a hard wooden bang with a short rattle
    d = 0.3; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        bang = (math.sin(2 * math.pi * 95 * t) * 0.8 + random.uniform(-1, 1) * 0.5) * math.exp(-t * 28)
        rattle = random.uniform(-1, 1) * 0.3 * math.exp(-t * 12) * (1 if (int(t * 60) % 2 == 0) else 0.3)
        out.append(bang + rattle * 0.5)
    write("slam", out)

    # Crusher warning: the hiss of the piston rising, crossfaded loop
    n = SR; xf = int(0.1 * SR)
    noise = [random.uniform(-1, 1) for _ in range(n + xf)]
    hiss = lowpass(highpass(noise, 0.85), 0.35)
    write("crusher_loop", seamless([s * 0.9 for s in hiss], n, xf))

    # Crusher strike: a hammer blow, the heavy impact and the ringing clang of steel on steel
    d = 0.9; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        impact = (math.sin(2 * math.pi * 75 * t) * 1.0 + random.uniform(-1, 1) * 0.9 * math.exp(-t * 90)) * math.exp(-t * 14)
        clang = (math.sin(2 * math.pi * 1180 * t) * 0.45 + math.sin(2 * math.pi * 2630 * t) * 0.3 + math.sin(2 * math.pi * 3930 * t) * 0.18 + math.sin(2 * math.pi * 5410 * t) * 0.1) * math.exp(-t * 7) * (1 - math.exp(-t * 400))
        out.append(math.tanh((impact + clang) * 1.4) * 0.98)
    write("crusher_on", out)

    # Laser beam: a high steady hum with a faint shimmer, crossfaded loop
    out = []
    for i in range(n + xf):
        t = i / SR
        out.append((math.sin(2 * math.pi * 880 * t) * 0.3 + math.sin(2 * math.pi * 1320 * t) * 0.12 + math.sin(2 * math.pi * 2640 * t) * 0.05) * (0.85 + 0.15 * math.sin(2 * math.pi * 11 * i / n)) * 0.25)
    write("laser_loop", seamless(out, n, xf))

    # Laser firing: a sharp rising chirp into the hum
    d = 0.25; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        f = 300 + 3000 * min(1.0, t / 0.08)
        out.append(math.sin(2 * math.pi * f * t) * 0.6 * min(1.0, t / 0.01) * (1 - t / d))
    write("laser_on", out)

    # Ice cracked open: sharp crackles over a cold whistle, crossfaded loop
    out = [math.sin(2 * math.pi * 1500 * i / SR) * 0.04 * (0.7 + 0.3 * math.sin(2 * math.pi * 1.5 * i / n)) for i in range(n + xf)]
    i = 0
    while i < n + xf:
        i += int(random.expovariate(14) * SR) + 1
        for j in range(min(int(0.012 * SR), n + xf - i)):
            out[i + j] += random.uniform(-1, 1) * 0.8 * math.exp(-j / (0.003 * SR))
    write("ice_loop", seamless(out, n, xf))

    # Ice cracking: one big splitting crack
    d = 0.35; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        out.append((random.uniform(-1, 1) * 0.9 * math.exp(-t * 25) + math.sin(2 * math.pi * 2200 * t) * 0.3 * math.exp(-t * 40)) * (1 if t < 0.3 else 0))
    for k in range(4):
        at = int((0.04 + 0.05 * k) * SR)
        for j in range(min(int(0.01 * SR), m - at)):
            out[at + j] += random.uniform(-1, 1) * 0.6 * math.exp(-j / (0.002 * SR))
    write("ice_on", out)

    # Spinner: a whirring vortex, a low whir with a rotating flutter, crossfaded loop
    out = []
    for i in range(n + xf):
        t = i / SR
        flutter = 0.6 + 0.4 * math.sin(2 * math.pi * 4 * i / n)
        out.append((math.sin(2 * math.pi * 140 * t) * 0.4 + math.sin(2 * math.pi * 283 * t) * 0.2) * flutter * 0.6)
    noise = lowpass(lowpass([random.uniform(-1, 1) for _ in range(n + xf)], 0.25), 0.4)
    out = [o + nz * 1.2 * (0.6 + 0.4 * math.sin(2 * math.pi * 4 * i / n)) for i, (o, nz) in enumerate(zip(out, noise))]
    write("spin_loop", seamless(out, n, xf))

    # Explosion: the ball bursting when time runs out, a deep boom with a crackling tail
    d = 0.9; m = int(d * SR)
    noise = [random.uniform(-1, 1) for _ in range(m)]
    boom = lowpass(lowpass(noise, 0.08), 0.3)
    out = []
    for i in range(m):
        t = i / SR
        low = math.sin(2 * math.pi * (55 + 40 * math.exp(-t * 12)) * t) * math.exp(-t * 5) * 0.9
        body = boom[i] * 6.0 * math.exp(-t * 6)
        crackle = noise[i] * 0.5 * math.exp(-t * 9) * (1 if random.random() < 0.35 else 0.2)
        out.append(min(1.0, max(-1.0, low + body + crackle)) * min(1.0, t / 0.004))
    write("explode", out)

    # Firework: a far, soft boom as the shell bursts, then a long crackling tail as the stars burn
    fr = random.Random(77)  # its own random numbers, so the sounds after it come out as they always did
    d = 2.2; m = int(d * SR)
    noise = [fr.uniform(-1, 1) for _ in range(m)]
    boom = lowpass(lowpass(noise, 0.05), 0.2)
    out = [0.0] * m
    for i in range(m):
        t = i / SR
        out[i] = (math.sin(2 * math.pi * (48 + 30 * math.exp(-t * 10)) * t) * math.exp(-t * 4) * 0.7 + boom[i] * 5.0 * math.exp(-t * 5)) * min(1.0, t / 0.006)
    for k in range(260):  # the crackle: little pops scattered through the tail, fewer as it dies
        at = int((0.25 + 1.8 * fr.random() ** 1.6) * SR)
        f = 1400 + 1600 * fr.random(); amp = 0.25 * (1 - (at / SR) / d) ** 1.2
        for j in range(min(int(0.008 * SR), m - at)):
            out[at + j] += math.sin(2 * math.pi * f * j / SR) * math.exp(-j / (0.0015 * SR)) * amp * fr.uniform(0.6, 1.0)
    peak = max(abs(v) for v in out)
    write("firework", [v / peak * 0.9 for v in out])

    # Disintegration: the ball vaporised by the laser, a bright sizzle that tears and fizzles away
    d = 1.0; m = int(d * SR)
    noise = [random.uniform(-1, 1) for _ in range(m)]
    bright = lowpass(highpass(noise, 0.92), 0.6)
    out = []
    for i in range(m):
        t = i / SR
        sizzle = bright[i] * 1.6 * math.exp(-t * 4.5) * (0.6 + 0.4 * math.sin(2 * math.pi * 37 * t))
        tear = math.sin(2 * math.pi * (2600 - 1900 * t) * t) * 0.35 * math.exp(-t * 6)
        crack = noise[i] * 0.9 * math.exp(-t * 40)
        out.append(max(-1.0, min(1.0, (sizzle + tear + crack) * min(1.0, t / 0.003))))
    i = 0
    while i < m:
        i += int(random.expovariate(50) * SR) + 1
        for j in range(min(int(0.003 * SR), m - i)):
            out[i + j] = max(-1.0, min(1.0, out[i + j] + random.uniform(-1, 1) * 0.6 * math.exp(-j / (0.0008 * SR)) * math.exp(-i / SR * 4)))
    write("disintegrate", out)

    # Reel tick: the faint mechanical click of the seconds drum stepping, dry and very short
    d = 0.035; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        out.append((math.sin(2 * math.pi * 1900 * t) * 0.5 + math.sin(2 * math.pi * 3800 * t) * 0.25 + random.uniform(-1, 1) * 0.35) * math.exp(-t * 180) * 0.6)
    write("reel_tick", out)

    # Spinner starting: the whir winding up
    d = 0.5; m = int(d * SR)
    out = []
    for i in range(m):
        t = i / SR
        k = min(1.0, t / 0.4)
        out.append(math.sin(2 * math.pi * (40 + 100 * k) * t) * 0.5 * k * (1 - max(0.0, (t - 0.4) / 0.1)))
    write("spin_on", out)


if __name__ == "__main__":
    main()
