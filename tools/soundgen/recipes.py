"""Every NOIRM sound, as a recipe. Each recipe takes a random generator and a variant number and
returns a mono float signal at 44.1 kHz. Variants differ in pitch, timing and random detail, so
the same hit never sounds exactly the same twice in a row.

The mood (docs/ART_STYLE.md): raw, tough, ink, high contrast. So: hard transients, a solid low
end, a short dark room, very little "sparkle", nothing cute or chiptune.

To tweak a sound: find its function below, change a number, and run
    python3 tools/soundgen/gen_sounds.py --only <event id>
"""
import numpy as np

from dsp import (SR, samples, t_axis, white, pink, brown, sine, saw, square, fm, glide, modal, bubble,
                 place, pad_to, env_ad, env_points, env_swell, smooth_random, fade, lowpass, highpass,
                 bandpass, peak, tilt, sweep_filter, biquad_bandpass, comb, saturate, resample, reverb,
                 mix, convolve, room_ir)


# ------------------------------------------------------------------ shared pieces

def vary(rng, amount=0.06):
    """A random pitch/time factor around 1."""
    return 1 + rng.uniform(-amount, amount)


def click(rng, length=0.006, lo=1500, hi=9000):
    """A hard, very short transient: the 'edge' of a hit."""
    n = white(length, rng) * env_ad(length, 0.0003, length / 5)
    return bandpass(n, lo, hi, order=1)


def thump(freq0, freq1, seconds, tau):
    """A body thump: a low sine falling in pitch, gone quickly."""
    f = glide(freq0, freq1, seconds)
    return sine(f) * env_ad(seconds, 0.002, tau)


def crack(rng, length=0.04, lo=300, hi=5000, tau=0.006):
    """One crack of rock or wood: a noise spike with a fast fall."""
    n = white(length, rng) * env_ad(length, 0.0005, tau)
    return bandpass(n, lo, hi, order=1)


def grains(seconds, rng, rate, make, wrap=False, density_env=None):
    """Scatter short grains (made by make(rng)) at random times: crackle, sand, debris, rain."""
    out = np.zeros(samples(seconds))
    t = 0.0
    while True:
        t += rng.exponential(1.0 / rate)
        if t >= seconds:
            break
        if density_env is not None and rng.random() > density_env(t / seconds):
            continue
        place(out, make(rng), int(t * SR), 1.0, wrap=wrap)
    return out


def whoosh(rng, seconds, f_lo, f_hi, peak_at=0.45, q=1.6, body=0.0):
    """Air pushed fast: noise through a band-pass that sweeps up then down, loudest mid-swing."""
    n = samples(seconds)
    x = np.linspace(0, 1, n)
    shape = np.exp(-((x - peak_at) / 0.22) ** 2)
    centers = f_lo + (f_hi - f_lo) * shape
    src = pink(seconds, rng) * 0.6 + white(seconds, rng) * 0.4
    out = sweep_filter(src, centers, q=q) * shape ** 1.3
    if body:
        out += lowpass(brown(seconds, rng), 250) * shape ** 2 * body
    return out


def loopify(x):
    """Removes any step at the loop point (the very end flows into the very start)."""
    return x - np.linspace(0, 1, len(x)) * (x[-1] - x[0])


# ------------------------------------------------------------------ hand seals

def seal_clap(rng, v):
    """Hands meeting: a dry cloth-and-skin clap with a little hollow body, soft and close."""
    p = vary(rng, 0.08)
    dur = 0.22
    rustle = bandpass(white(0.07, rng), 1800, 6500) * env_points(0.07, [(0, 0), (0.045, 1), (0.07, 0)])
    rustle = np.concatenate([rustle, np.zeros(samples(dur))])[:samples(dur)]
    slap = bandpass(white(0.05, rng), 700 * p, 3800 * p, order=2) * env_ad(0.05, 0.0006, 0.009)
    body = thump(260 * p, 190 * p, 0.06, 0.018)
    x = np.zeros(samples(dur))
    hit = int(0.05 * SR)
    place(x, rustle, 0, 0.18)
    place(x, slap, hit, 1.0)
    place(x, body, hit, 0.45)
    place(x, click(rng, 0.004, 2500, 9000), hit, 0.4)
    return reverb(x, rng, 0.25, 0.05, wet=0.12, brightness=6000)


def seal_complete(rng, v):
    """The seals lock: chakra rushes up through the hands, a soft whoosh with a low pulse."""
    p = vary(rng, 0.05)
    dur = 0.95
    air = whoosh(rng, dur, 350 * p, 2600 * p, peak_at=0.55, q=1.3)
    t = t_axis(dur)
    pulse_env = env_swell(dur, 0.55, power=2.2, release=0.18)
    pulse = (sine(glide(70 * p, 110 * p, dur)) + 0.4 * sine(glide(140 * p, 220 * p, dur))) * pulse_env
    shimmer = bandpass(white(dur, rng), 3000, 9000) * pulse_env ** 3 * 0.25
    x = mix((air, 1.0), (pulse, 0.55), (shimmer, 1.0))
    return reverb(saturate(x * 0.9, 1.5), rng, 0.8, 0.18, wet=0.2)


# ------------------------------------------------------------------ chakra

def chakra_charge(rng, v):
    """Chakra gathering (a seamless loop): a low, beating hum with breathing air around it."""
    p = vary(rng, 0.04)
    dur = 3.0  # the LFOs below complete whole cycles in 3 s, so the loop is seamless
    t = t_axis(dur)
    f0 = 73.3 * p
    hum = np.zeros_like(t)
    for k, (a, det) in enumerate([(1.0, 0.0), (0.55, 0.0), (0.35, 0.0), (0.22, 0.0)], start=1):
        fk = round(f0 * k * dur) / dur  # whole cycles per loop
        hum += a * np.sin(2 * np.pi * fk * t)
        beat = round((f0 * k + 1.0 + k * 0.33) * dur) / dur
        hum += a * 0.5 * np.sin(2 * np.pi * beat * t)
    swell = 0.75 + 0.25 * np.sin(2 * np.pi * (2 / dur) * t)
    air = bandpass(pink(dur, rng, circular=True), 600, 3200, circular=True)
    air *= 0.6 + 0.4 * np.sin(2 * np.pi * (3 / dur) * t + 1.0)
    x = saturate(hum * swell * 0.35, 1.8) + air * 0.08
    return loopify(lowpass(x, 3500, circular=True))


def chakra_release(rng, v):
    """Chakra let go: a punchy low burst and a blast of air sweeping down."""
    p = vary(rng, 0.06)
    dur = 1.0
    boom = thump(110 * p, 42 * p, 0.5, 0.12)
    n = samples(dur)
    centers = np.geomspace(4000 * p, 300 * p, n)
    blast = sweep_filter(white(dur, rng), centers, q=0.9) * env_ad(dur, 0.004, 0.18)
    x = mix((boom, 0.9), (blast, 0.8), (click(rng, 0.008, 800, 6000), 0.6))
    return reverb(saturate(x, 2.0), rng, 1.0, 0.22, wet=0.22)


# ------------------------------------------------------------------ combat

def hit_light(rng, v):
    """A fast jab or kick landing: a tight thump, a slap of cloth and a crisp edge."""
    p = vary(rng, 0.1)
    dur = 0.3
    body = thump(190 * p, 95 * p, 0.2, 0.04)
    slap = bandpass(white(0.08, rng), 900 * p, 4200 * p) * env_ad(0.08, 0.0006, 0.014)
    x = mix((body, 0.8), (slap, 1.1), (click(rng, 0.005, 2000, 9000), 0.8))
    x = pad_to(x, dur)
    return reverb(saturate(x, 2.4), rng, 0.3, 0.06, wet=0.1)


def hit_heavy(rng, v):
    """A heavy blow: deep thump with a crunchy mid, a cracking edge, and a short dark room."""
    p = vary(rng, 0.08)
    dur = 0.65
    body = thump(120 * p, 38 * p, 0.45, 0.11)
    crunch = bandpass(white(0.2, rng), 250 * p, 1600 * p) * env_ad(0.2, 0.001, 0.035)
    crunch = saturate(crunch * 3, 3)
    slap = bandpass(white(0.1, rng), 1200, 5000) * env_ad(0.1, 0.0005, 0.012)
    x = mix((body, 1.0), (crunch, 0.45), (slap, 0.5), (click(rng, 0.006, 1500, 8000), 0.6))
    x = pad_to(x, dur)
    return reverb(saturate(x, 2.8), rng, 0.6, 0.12, wet=0.16, brightness=3500)


def guard(rng, v):
    """A blow taken on the guard: a dull forearm knock, leather and a muffled thud."""
    p = vary(rng, 0.08)
    dur = 0.35
    knock = modal([310 * p, 720 * p, 1270 * p], [0.05, 0.025, 0.012], [1.0, 0.5, 0.25], dur, rng)
    body = thump(180 * p, 110 * p, 0.12, 0.03)
    leather = bandpass(white(0.06, rng), 600, 2600) * env_ad(0.06, 0.0008, 0.01)
    x = mix((knock, 0.5), (body, 0.8), (leather, 0.6))
    return reverb(saturate(x, 1.8), rng, 0.3, 0.06, wet=0.1)


def parry(rng, v):
    """A clean parry: a hard metallic clang (inharmonic bar partials) that rings out."""
    p = vary(rng, 0.07)
    dur = 1.3
    f0 = 520 * p
    ratios = [1.0, 2.756, 5.404, 8.933, 13.34, 1.51, 3.9]
    decays = [0.7, 0.45, 0.28, 0.16, 0.08, 0.35, 0.2]
    amps = [1.0, 0.7, 0.5, 0.35, 0.2, 0.3, 0.25]
    ring = modal([f0 * r for r in ratios], decays, amps, dur, rng, jitter=0.01)
    strike = bandpass(white(0.03, rng), 2000, 12000) * env_ad(0.03, 0.0003, 0.004)
    x = mix((ring, 0.6), (strike, 0.9), (thump(200, 120, 0.08, 0.02), 0.4))
    return reverb(saturate(x, 1.4), rng, 1.2, 0.3, wet=0.2, brightness=7000)


def just_frame(rng, v):
    """A perfect, just-in-time parry: a bright, clear ring hanging in the air."""
    p = vary(rng, 0.03)
    dur = 1.6
    bell = fm(1320 * p, 1.4, 2.5, dur, env_ad(dur, 0.001, 0.15)) * env_ad(dur, 0.001, 0.5)
    upper = sine(2640 * p * 1.003, dur) * env_ad(dur, 0.001, 0.3)
    x = mix((bell, 0.7), (upper, 0.25), (parry(rng, v), 0.5))
    return reverb(x, rng, 1.5, 0.45, wet=0.3, brightness=8000)


def posture_break(rng, v):
    """The guard caves in: a heavy crunch, splintering cracks and a falling metallic groan."""
    p = vary(rng, 0.06)
    dur = 1.0
    body = thump(95 * p, 35 * p, 0.6, 0.18)
    cracks = grains(0.25, rng, 70, lambda r: crack(r, 0.03, 400, 6000, 0.004) * r.uniform(0.3, 1))
    groan = modal([380 * p, 1050 * p, 1900 * p], [0.25, 0.12, 0.06], [1, 0.5, 0.3], dur, rng)
    groan *= 1.0
    fall = sine(glide(700 * p, 180 * p, dur)) * env_ad(dur, 0.01, 0.15) * 0.3
    x = mix((body, 1.0), (pad_to(cracks, dur), 0.7), (groan, 0.35), (fall, 0.6),
            (click(rng, 0.008, 1000, 8000), 0.7))
    return reverb(saturate(x, 2.5), rng, 0.9, 0.2, wet=0.2, brightness=4000)


def swing_light(rng, v):
    """A quick strike cutting air: short and bright."""
    p = vary(rng, 0.12)
    dur = 0.22 * vary(rng, 0.1)
    return whoosh(rng, dur, 700 * p, 3000 * p, peak_at=rng.uniform(0.4, 0.6), q=1.8)


def swing_heavy(rng, v):
    """A big swing: a deeper, longer rush of air with weight under it."""
    p = vary(rng, 0.1)
    dur = 0.42 * vary(rng, 0.1)
    return whoosh(rng, dur, 280 * p, 1500 * p, peak_at=rng.uniform(0.45, 0.6), q=1.4, body=0.6)


def body_thud(rng, v):
    """A body hitting the ground: a heavy low thud, cloth and a scatter of dirt."""
    p = vary(rng, 0.08)
    dur = 0.7
    body = thump(85 * p, 40 * p, 0.4, 0.09)
    mid = lowpass(white(0.15, rng), 900) * env_ad(0.15, 0.001, 0.03)
    dirt = grains(0.35, rng, 60, lambda r: crack(r, 0.02, 800, 4000, 0.003) * r.uniform(0.1, 0.5),
                  density_env=lambda u: 1 - u)
    x = mix((body, 1.0), (mid, 0.6), (place(np.zeros(samples(dur)), dirt, samples(0.02)), 0.5))
    return reverb(saturate(x, 2.2), rng, 0.5, 0.1, wet=0.12, brightness=3000)


def wall_splat(rng, v):
    """Slammed into a wall: a big impact, crumbling stone and a short boom."""
    p = vary(rng, 0.06)
    dur = 1.3
    body = thump(70 * p, 30 * p, 0.8, 0.2)
    mid = saturate(bandpass(white(0.3, rng), 150, 1200) * env_ad(0.3, 0.001, 0.05) * 3, 3)
    crumble = grains(0.9, rng, 45, lambda r: crack(r, 0.05, 200, 3500, r.uniform(0.004, 0.015)) * r.uniform(0.2, 0.8),
                     density_env=lambda u: (1 - u) ** 1.5)
    x = mix((body, 1.0), (mid, 0.5), (place(np.zeros(samples(dur)), crumble, samples(0.03)), 0.55),
            (click(rng, 0.01, 800, 7000), 0.7))
    return reverb(saturate(x, 2.6), rng, 1.0, 0.25, wet=0.2, brightness=3000)


# ------------------------------------------------------------------ weapons

def weapon_throw(rng, v):
    """A kunai or shuriken leaving the hand: a thin fast whoosh with a faint spinning whistle."""
    p = vary(rng, 0.1)
    dur = 0.28
    air = whoosh(rng, dur, 1400 * p, 5200 * p, peak_at=0.35, q=2.2)
    t = t_axis(dur)
    whistle = sine(2600 * p * (1 + 0.04 * np.sin(2 * np.pi * 38 * t))) * np.exp(-((t / dur - 0.35) / 0.25) ** 2)
    flick = bandpass(white(0.03, rng), 1500, 6000) * env_ad(0.03, 0.0005, 0.005)
    return mix((air, 1.0), (whistle, 0.08 + 0.06 * (v % 2)), (flick, 0.4))


def weapon_impact(rng, v):
    """A thrown blade biting home: a sharp tick, a woody thunk and a tiny metal shiver."""
    p = vary(rng, 0.1)
    dur = 0.4
    thunk = modal([380 * p, 900 * p, 1700 * p], [0.04, 0.02, 0.01], [1, 0.5, 0.3], dur, rng)
    shiver = modal([3100 * p, 4700 * p, 6900 * p], [0.12, 0.08, 0.05], [1, 0.7, 0.4], dur, rng, jitter=0.02)
    x = mix((thunk, 0.8), (shiver, 0.15), (click(rng, 0.004, 2000, 10000), 0.8), (thump(160, 90, 0.08, 0.02), 0.4))
    return reverb(saturate(x, 1.6), rng, 0.3, 0.06, wet=0.1)


def weapon_clash(rng, v):
    """Two blades meeting: a sharp metal strike, a scrape and a long bright ring."""
    p = vary(rng, 0.06)
    dur = 1.1
    f0 = 1150 * p
    ring = modal([f0 * r for r in (1.0, 1.73, 2.61, 3.47, 4.9, 6.3)], [0.55, 0.4, 0.3, 0.2, 0.12, 0.08],
                 [1, 0.8, 0.6, 0.5, 0.3, 0.2], dur, rng, jitter=0.02)
    scrape_len = 0.18
    scrape = bandpass(white(scrape_len, rng), 2500, 9000) * env_ad(scrape_len, 0.002, 0.05)
    scrape *= 0.6 + 0.4 * np.abs(np.sin(2 * np.pi * rng.uniform(70, 110) * t_axis(scrape_len)))
    x = mix((ring, 0.45), (pad_to(scrape, dur), 0.5), (click(rng, 0.004, 3000, 12000), 0.9))
    return reverb(saturate(x, 1.4), rng, 1.0, 0.25, wet=0.2, brightness=8000)


# ------------------------------------------------------------------ fire

def fire_roar(rng, v):
    """A torrent of flame: a deep breathing roar with a whoomph at the start and crackle inside."""
    p = vary(rng, 0.06)
    dur = 1.9
    whoomph = thump(90 * p, 45 * p, 0.4, 0.12)
    body = lowpass(brown(dur, rng) * 0.6 + pink(dur, rng) * 0.4, 1400 * p)
    flutter = 0.7 + 0.3 * smooth_random(dur, rng, 9)
    shape = env_points(dur, [(0, 0), (0.12, 1), (1.3, 0.85), (dur, 0)])
    roar = saturate(body * flutter * shape * 1.4, 2.0)
    crackle = grains(dur, rng, 25, lambda r: crack(r, 0.015, 1200, 7000, 0.0015) * r.uniform(0.2, 1))
    x = mix((roar, 0.8), (whoomph, 0.7), (crackle * shape, 0.35))
    return reverb(x, rng, 1.0, 0.25, wet=0.18, brightness=3500)


def fire_crackle(rng, v):
    """Burning: pops and snaps of different sizes over a soft low flutter."""
    p = vary(rng, 0.1)
    dur = 1.6
    bed = lowpass(brown(dur, rng), 500 * p) * (0.6 + 0.4 * smooth_random(dur, rng, 4)) * 0.25
    small = grains(dur, rng, 35, lambda r: crack(r, 0.012, 1500, 8000, 0.001) * r.uniform(0.1, 0.6))
    big = grains(dur, rng, 6, lambda r: crack(r, 0.04, 400, 5000, 0.004) * r.uniform(0.5, 1))
    x = mix((bed, 1.0), (small, 0.6), (big, 0.9))
    return fade(x, 0.05, 0.25)


def fire_burst(rng, v):
    """A fireball bursting: a fat whoomph, a blast that darkens as it spreads, then embers."""
    p = vary(rng, 0.07)
    dur = 1.4
    whoomph = thump(100 * p, 35 * p, 0.6, 0.14)
    n = samples(dur)
    blast = sweep_filter(pink(dur, rng) * 0.5 + white(dur, rng) * 0.5, np.geomspace(5000 * p, 350 * p, n), q=0.8)
    blast *= env_ad(dur, 0.003, 0.25)
    embers = grains(dur, rng, 18, lambda r: crack(r, 0.012, 2000, 8000, 0.0015) * r.uniform(0.1, 0.7),
                    density_env=lambda u: u)
    x = mix((whoomph, 0.9), (saturate(blast * 2, 2), 0.6), (embers, 0.4))
    return reverb(saturate(x, 2.0), rng, 1.2, 0.3, wet=0.2, brightness=3500)


# ------------------------------------------------------------------ water

def _bubbles(seconds, rng, rate, lo, hi, env=None):
    return grains(seconds, rng, rate, lambda r: bubble(r.uniform(lo, hi), 0.08, r.uniform(1.3, 2.0),
                                                       r.uniform(0.01, 0.035)) * r.uniform(0.2, 1), density_env=env)


def water_surge(rng, v):
    """A wave of water: a rushing body with churning bubbles, rising then rolling away."""
    p = vary(rng, 0.06)
    dur = 1.8
    n = samples(dur)
    shape = env_points(dur, [(0, 0), (0.35, 1), (1.1, 0.8), (dur, 0)])
    rush = sweep_filter(pink(dur, rng), 600 * p + 900 * p * (0.5 + 0.5 * smooth_random(dur, rng, 3)), q=0.9)
    churn = _bubbles(dur, rng, 90, 300 * p, 1400 * p)
    low = lowpass(brown(dur, rng), 220) * 0.5
    x = mix((rush * shape, 0.9), (churn * shape, 0.45), (low * shape, 0.6))
    return reverb(saturate(x, 1.5), rng, 0.8, 0.2, wet=0.15, brightness=4000)


def water_splash(rng, v):
    """Water hitting hard: a slap, a spray, a low plop and bubbles fizzing out."""
    p = vary(rng, 0.08)
    dur = 1.0
    slap = highpass(white(0.15, rng), 900) * env_ad(0.15, 0.001, 0.04)
    spray = bandpass(white(0.6, rng), 2500, 9000) * env_ad(0.6, 0.01, 0.12)
    plop = bubble(140 * p, 0.25, 1.8, 0.05)
    fizz = _bubbles(dur, rng, 70, 500 * p, 2500 * p, env=lambda u: (1 - u) ** 2)
    x = mix((slap, 0.8), (spray, 0.35), (plop, 0.8), (fizz, 0.45), (thump(120, 60, 0.15, 0.04), 0.5))
    return reverb(saturate(x, 1.5), rng, 0.6, 0.15, wet=0.15)


# ------------------------------------------------------------------ lightning

def lightning_crackle(rng, v):
    """Lightning held in the hand (a seamless loop): a harsh electric buzz full of spitting sparks."""
    p = vary(rng, 0.05)
    dur = 2.0
    t = t_axis(dur)
    f0 = round(96 * p * dur) / dur
    buzz = np.zeros_like(t)
    for k in range(1, 30):
        buzz += np.sin(2 * np.pi * f0 * k * t + rng.uniform(0, 6.28)) / k ** 0.7
    buzz = saturate(buzz * 0.4, 3)
    jitter = 0.5 + 0.5 * np.abs(smooth_random(dur, rng, 30, circular=True))
    buzz = highpass(buzz, 300, circular=True) * jitter
    sparks = grains(dur, rng, 55, lambda r: crack(r, 0.01, 3000, 14000, 0.0008) * r.uniform(0.2, 1), wrap=True)
    chirps = grains(dur, rng, 9, lambda r: sine(glide(r.uniform(2500, 5000), r.uniform(600, 1200), 0.05))
                    * env_ad(0.05, 0.001, 0.01) * r.uniform(0.3, 0.7), wrap=True)
    x = mix((buzz, 0.35), (sparks, 0.7), (chirps, 0.35))
    return loopify(highpass(x, 150, circular=True))


def lightning_snap(rng, v):
    """A lightning strike up close: a whip-crack, a sizzling zap, and a short rolling tail."""
    p = vary(rng, 0.08)
    dur = 0.9
    snap = highpass(white(0.02, rng), 1200) * env_ad(0.02, 0.0002, 0.003)
    zap = sine(glide(4200 * p, 180 * p, 0.12)) * env_ad(0.12, 0.0005, 0.04)
    zap = saturate(zap * 2, 3)
    fizz = bandpass(white(0.35, rng), 3000, 12000) * env_ad(0.35, 0.001, 0.07) * (0.5 + 0.5 * np.sign(
        np.sin(2 * np.pi * 70 * t_axis(0.35))))
    roll = lowpass(brown(dur, rng), 300) * env_points(dur, [(0, 0), (0.05, 1), (0.4, 0.4), (dur, 0)])
    x = mix((snap, 1.0), (zap, 0.5), (fizz, 0.5), (roll, 0.2), (thump(110, 45, 0.25, 0.06), 0.45))
    return reverb(saturate(x, 2.2), rng, 1.0, 0.22, wet=0.18, brightness=6000)


# ------------------------------------------------------------------ wind

def wind_gust(rng, v):
    """A blast of wind: a swelling rush whose pitch wanders, with a hollow whistle in it."""
    p = vary(rng, 0.08)
    dur = 1.6
    n = samples(dur)
    shape = env_points(dur, [(0, 0), (0.4, 1), (0.9, 0.9), (dur, 0)])
    centers = (500 + 350 * smooth_random(dur, rng, 2.5)) * p
    rush = sweep_filter(pink(dur, rng) * 0.7 + white(dur, rng) * 0.3, centers, q=1.4)
    hollow = sweep_filter(white(dur, rng), centers * 2.2, q=9)
    low = lowpass(brown(dur, rng), 160) * 0.6
    return mix((rush * shape, 1.0), (hollow * shape, 0.35), (low * shape, 0.5))


def wind_whistle(rng, v):
    """A slicing blade of wind: a sharp shrieking whistle that dives in pitch as it cuts past."""
    p = vary(rng, 0.08)
    dur = 0.65
    n = samples(dur)
    centers = np.geomspace(4200 * p, 1300 * p, n)
    shape = env_points(dur, [(0, 0), (0.08, 1), (0.3, 0.7), (dur, 0)])
    tone = sweep_filter(white(dur, rng), centers, q=14) * shape
    air = whoosh(rng, dur, 800 * p, 3500 * p, peak_at=0.2, q=1.5)
    edge = click(rng, 0.006, 3000, 12000)
    return mix((tone, 1.0), (air, 0.6), (edge, 0.4))


# ------------------------------------------------------------------ earth

def earth_rumble(rng, v):
    """The ground stirring: a deep shaking rumble with grinding stones."""
    p = vary(rng, 0.08)
    dur = 2.2
    shape = env_points(dur, [(0, 0), (0.3, 1), (1.5, 0.8), (dur, 0)])
    rumble = lowpass(brown(dur, rng), 160 * p) * (0.7 + 0.3 * smooth_random(dur, rng, 12))
    grind = bandpass(brown(dur, rng), 150, 900) * (0.5 + 0.5 * smooth_random(dur, rng, 20)) * 0.5
    stones = grains(dur, rng, 30, lambda r: crack(r, 0.04, 150, 2000, r.uniform(0.004, 0.012)) * r.uniform(0.1, 0.6))
    x = mix((saturate(rumble * 1.4, 2), 1.0), (grind, 0.6), (lowpass(stones, 1500), 0.15))
    return x * shape


def earth_crack(rng, v):
    """Rock splitting: a few hard cracks in a burst, a heavy thump and falling grit."""
    p = vary(rng, 0.08)
    dur = 1.0
    x = np.zeros(samples(dur))
    t = 0.0
    for i in range(rng.integers(3, 6)):
        place(x, crack(rng, 0.06, 200 * p, 4500 * p, rng.uniform(0.004, 0.01)) * (1.0 - i * 0.12), samples(t))
        t += rng.uniform(0.012, 0.04)
    body = thump(80 * p, 35 * p, 0.45, 0.1)
    grit = grains(0.7, rng, 40, lambda r: crack(r, 0.02, 800, 4000, 0.002) * r.uniform(0.05, 0.4),
                  density_env=lambda u: 1 - u)
    x = mix((x, 1.0), (body, 0.9), (place(np.zeros(samples(dur)), grit, samples(0.08)), 0.5))
    return reverb(saturate(x, 2.2), rng, 0.9, 0.2, wet=0.18, brightness=3000)


# ------------------------------------------------------------------ combined natures

def ice_shatter(rng, v):
    """Ice breaking apart: a glassy crack, then a spray of tiny ringing shards."""
    p = vary(rng, 0.06)
    dur = 1.2
    x = np.zeros(samples(dur))
    place(x, crack(rng, 0.03, 2000, 12000, 0.003), 0, 1.0)
    for _ in range(rng.integers(22, 34)):
        at = rng.exponential(0.12)
        if at >= dur - 0.2:
            continue
        f = rng.uniform(2500, 9000) * p
        shard = modal([f, f * 1.47, f * 2.31], [rng.uniform(0.02, 0.08)] * 3, [1, 0.5, 0.3], 0.25, rng)
        place(x, shard, samples(at), rng.uniform(0.15, 0.6) * np.exp(-at * 2))
    x = mix((x, 1.0), (thump(220, 120, 0.1, 0.02), 0.4))
    return reverb(x, rng, 1.0, 0.25, wet=0.22, brightness=9000)


def lava_hiss(rng, v):
    """Lava: a thick heavy hiss with slow, gloopy bubbles bursting underneath."""
    p = vary(rng, 0.07)
    dur = 1.8
    shape = env_points(dur, [(0, 0), (0.2, 1), (1.3, 0.8), (dur, 0)])
    hiss = bandpass(white(dur, rng), 2500, 9000) * (0.6 + 0.4 * smooth_random(dur, rng, 6)) * 0.4
    gloop = grains(dur, rng, 9, lambda r: bubble(r.uniform(70, 160) * p, 0.25, r.uniform(1.6, 2.6), r.uniform(0.04, 0.08))
                   * r.uniform(0.5, 1))
    rumble = lowpass(brown(dur, rng), 120) * 0.5
    pops = grains(dur, rng, 6, lambda r: crack(r, 0.03, 300, 2500, 0.006) * r.uniform(0.3, 0.8))
    x = mix((hiss * shape, 1.0), (saturate(gloop * 1.5, 2), 0.7), (rumble * shape, 0.7), (pops, 0.5))
    return reverb(x, rng, 0.8, 0.2, wet=0.15, brightness=4000)


def boil_steam(rng, v):
    """Boiling steam: pressure building into a scalding jet, with rolling bubbles under it."""
    p = vary(rng, 0.06)
    dur = 1.7
    n = samples(dur)
    shape = env_points(dur, [(0, 0), (0.5, 0.6), (0.65, 1), (1.3, 0.7), (dur, 0)])
    jet = sweep_filter(white(dur, rng), np.geomspace(1500 * p, 5000 * p, n), q=1.2) * shape
    whistle = sine(glide(1900 * p, 2600 * p, dur)) * shape ** 3 * 0.05
    boil = _bubbles(dur, rng, 120, 200 * p, 900 * p)
    return reverb(mix((jet, 0.8), (whistle, 1.0), (boil, 0.45)), rng, 0.8, 0.2, wet=0.15)


def storm_hum(rng, v):
    """The storm laser (a seamless loop): a pulsing electric drone with a high whine above it."""
    p = vary(rng, 0.04)
    dur = 2.0
    t = t_axis(dur)
    base = round(110 * p * dur) / dur
    drone = saw(np.full(len(t), base), harmonics=25) + saw(np.full(len(t), base + 1.5), harmonics=25)
    pulse = 0.65 + 0.35 * np.sin(2 * np.pi * (12 / dur) * t)
    whine_f = round(1760 * p * dur) / dur
    whine = np.sin(2 * np.pi * whine_f * t + 3 * np.sin(2 * np.pi * (14 / dur) * t))
    sparks = grains(dur, rng, 25, lambda r: crack(r, 0.008, 4000, 14000, 0.0007) * r.uniform(0.2, 0.7), wrap=True)
    x = mix((lowpass(saturate(drone * 0.4, 2), 2500, circular=True) * pulse, 0.6), (whine, 0.1), (sparks, 0.35))
    return loopify(highpass(x, 60, circular=True))


def explosion_boom(rng, v):
    """An explosion: a huge sub boom, a blast front, cracking debris and a rolling tail."""
    p = vary(rng, 0.06)
    dur = 2.6
    sub = thump(70 * p, 26 * p, 1.6, 0.45)
    n = samples(dur)
    blast = sweep_filter(white(dur, rng) * 0.5 + pink(dur, rng) * 0.5, np.geomspace(6000, 250, n), q=0.7)
    blast *= env_ad(dur, 0.002, 0.3)
    debris = grains(dur, rng, 30, lambda r: crack(r, 0.04, 300, 4000, r.uniform(0.003, 0.01)) * r.uniform(0.1, 0.6),
                    density_env=lambda u: np.exp(-u * 3))
    roll = lowpass(brown(dur, rng), 200) * env_points(dur, [(0, 0), (0.1, 1), (0.9, 0.5), (dur, 0)])
    x = mix((sub, 1.0), (saturate(blast * 2.5, 3), 0.7), (debris, 0.5), (roll, 0.6), (click(rng, 0.01, 500, 6000), 0.7))
    return reverb(saturate(x, 2.5), rng, 1.8, 0.5, wet=0.25, brightness=2500)


def scorch_sizzle(rng, v):
    """Scorching heat: an angry frying sizzle with tiny spits."""
    p = vary(rng, 0.08)
    dur = 1.4
    shape = env_points(dur, [(0, 0), (0.08, 1), (0.9, 0.8), (dur, 0)])
    fry = highpass(white(dur, rng), 3000 * p) * (0.4 + 0.6 * np.abs(smooth_random(dur, rng, 40))) * 0.45
    spits = grains(dur, rng, 80, lambda r: crack(r, 0.006, 3000, 12000, 0.0007) * r.uniform(0.2, 1))
    heat = bandpass(pink(dur, rng), 400, 1500) * 0.25
    return mix((fry * shape, 1.0), (spits * shape, 0.8), (heat * shape, 1.0))


def dust_hum(rng, v):
    """Dust Release: a pure, eerie chord that rises while matter crumbles to powder."""
    p = vary(rng, 0.03)
    dur = 1.9
    shape = env_points(dur, [(0, 0), (0.4, 1), (1.4, 0.9), (dur, 0)])
    chord = sum(a * sine(glide(f * p, f * p * 1.06, dur)) for f, a in ((220, 1.0), (330, 0.6), (440, 0.4), (660, 0.25)))
    rise = sine(glide(880 * p, 1760 * p, dur)) * env_swell(dur, 1.5, 2.0, 0.2) * 0.25
    grit = grains(dur, rng, 160, lambda r: bandpass(white(0.008, r), 3000, 12000) * env_ad(0.008, 0.0005, 0.002)
                  * r.uniform(0.1, 0.5), density_env=lambda u: u)
    x = mix((chord * shape * 0.35, 1.0), (rise, 1.0), (grit, 0.5))
    return reverb(x, rng, 1.2, 0.35, wet=0.25, brightness=7000)


def magnet_hiss(rng, v):
    """Iron sand: a dense metallic pour of grains, hissing as it swirls."""
    p = vary(rng, 0.08)
    dur = 1.5
    shape = env_points(dur, [(0, 0), (0.3, 1), (1.0, 0.85), (dur, 0)])
    sand = grains(dur, rng, 900, lambda r: bandpass(white(0.004, r), 1500, 9000) * env_ad(0.004, 0.0002, 0.0008)
                  * r.uniform(0.1, 1))
    sand = comb(sand, 1 / (620 * p), 0.6)
    swirl = sweep_filter(white(dur, rng), 1800 * p + 1200 * p * smooth_random(dur, rng, 3), q=3) * 0.4
    return mix((sand * shape, 1.0), (swirl * shape, 1.0), (lowpass(brown(dur, rng), 150) * shape, 0.3))


def wood_creak(rng, v):
    """Living wood bending: a slow, groaning stick-slip creak through hollow timber."""
    p = vary(rng, 0.1)
    dur = 1.3
    n = samples(dur)
    rate = 40 * p + 30 * p * (0.5 + 0.5 * smooth_random(dur, rng, 2))
    phase = np.cumsum(rate) / SR
    ticks = np.diff(np.floor(phase), prepend=0) > 0
    pulses = ticks.astype(float) * (0.5 + 0.5 * rng.random(n))
    pulses = lowpass(pulses, 3000)
    body = biquad_bandpass(pulses, 330 * p, 6) + 0.7 * biquad_bandpass(pulses, 820 * p, 8) + 0.4 * biquad_bandpass(pulses, 1650 * p, 10)
    shape = env_points(dur, [(0, 0), (0.15, 1), (1.0, 0.8), (dur, 0)])
    return reverb(saturate(body * shape * 3, 1.5), rng, 0.6, 0.12, wet=0.12, brightness=3000)


def wood_burst(rng, v):
    """Wood bursting out of the ground: splintering cracks over a heavy thump and a creak."""
    p = vary(rng, 0.08)
    dur = 1.3
    splinters = grains(0.35, rng, 60, lambda r: crack(r, 0.04, 300, 6000, r.uniform(0.003, 0.008)) * r.uniform(0.3, 1))
    body = thump(95 * p, 40 * p, 0.5, 0.12)
    creak = wood_creak(rng, v)[:samples(dur)]
    x = mix((pad_to(splinters, dur), 0.9), (body, 0.9), (creak, 0.3), (click(rng, 0.008, 600, 6000), 0.6))
    return reverb(saturate(x, 2.0), rng, 1.0, 0.25, wet=0.18, brightness=3500)


# ------------------------------------------------------------------ yin, yang, medical

def yin_swell(rng, v):
    """Yin: a dark, reversed swell, a low cluster sucked backwards into silence."""
    p = vary(rng, 0.04)
    dur = 1.7
    tone = sum(a * sine(f * p, dur) for f, a in ((55, 1.0), (65.4, 0.7), (82.4, 0.5), (110 * 1.007, 0.4), (155.6, 0.25)))
    air = bandpass(pink(dur, rng), 200, 1500) * 0.4
    struck = (tone * 0.4 + air) * env_ad(dur, 0.005, 0.5)
    wet = reverb(struck, rng, 1.6, 0.6, wet=0.6, brightness=2000)[:samples(dur)]
    rev = wet[::-1]
    rev *= env_points(dur, [(0, 0), (dur - 0.08, 1), (dur, 0)])
    return saturate(rev * 1.3, 1.3)


def yang_swell(rng, v):
    """Yang: a warm swell, a bright major chord blooming like sunlight."""
    p = vary(rng, 0.03)
    dur = 1.9
    shape = env_points(dur, [(0, 0), (0.55, 1), (1.1, 0.8), (dur, 0)])
    chord = 0
    for f, a in ((110, 1.0), (164.8, 0.7), (220, 0.6), (277.2, 0.45), (329.6, 0.35), (440, 0.2)):
        for d in (-0.004, 0.004):
            chord = chord + a * saw(np.full(samples(dur), f * p * (1 + d)), harmonics=8)
    chord = lowpass(chord, 1600)
    glow = bandpass(pink(dur, rng), 1500, 5000) * 0.1
    x = (chord * 0.18 + glow) * shape
    return reverb(x, rng, 1.4, 0.4, wet=0.3, brightness=5000)


def medical_chime(rng, v):
    """Medical ninjutsu: a soft, clear healing chime with a gentle halo."""
    p = vary(rng, 0.04)
    dur = 1.6
    f = 1046 * p
    bell = fm(f, 3.5, 1.2, dur, env_ad(dur, 0.002, 0.2)) * env_ad(dur, 0.006, 0.45)
    halo = sine(f * 2.001, dur) * env_ad(dur, 0.05, 0.4) * 0.2 + sine(f * 0.5, dur) * env_ad(dur, 0.1, 0.5) * 0.25
    return reverb(mix((bell, 0.7), (halo, 1.0)), rng, 1.5, 0.5, wet=0.35, brightness=7000)


# ------------------------------------------------------------------ big moments

def big_impact(rng, v):
    """An S-rank impact: a colossal, ground-shaking boom with a long sub tail."""
    p = vary(rng, 0.05)
    dur = 3.6
    sub = thump(55 * p, 22 * p, 3.0, 0.9)
    punch = thump(140 * p, 50 * p, 0.3, 0.07)
    n = samples(dur)
    blast = sweep_filter(white(dur, rng) * 0.4 + brown(dur, rng) * 0.6, np.geomspace(3500, 120, n), q=0.6)
    blast *= env_ad(dur, 0.002, 0.45)
    debris = grains(dur, rng, 25, lambda r: crack(r, 0.06, 150, 2500, r.uniform(0.005, 0.015)) * r.uniform(0.1, 0.5),
                    density_env=lambda u: np.exp(-u * 2.5))
    x = mix((sub, 1.0), (punch, 0.8), (saturate(blast * 2.5, 3), 0.6), (debris, 0.4), (click(rng, 0.012, 400, 5000), 0.6))
    return reverb(saturate(x, 2.4), rng, 2.5, 0.8, wet=0.28, brightness=1800)


def big_genjutsu(rng, v):
    """Genjutsu: a warped, wavering tone; the world bending out of tune."""
    p = vary(rng, 0.04)
    dur = 2.6
    t = t_axis(dur)
    bend = 1 + 0.05 * np.sin(2 * np.pi * 0.7 * t) + 0.02 * smooth_random(dur, rng, 3)
    a = sine(330 * p * bend)
    b = sine(331.8 * p / bend * 1.5)
    ring = a * sine(glide(57 * p, 41 * p, dur))
    swell = env_points(dur, [(0, 0), (0.7, 1), (1.8, 0.85), (dur, 0)])
    x = (a * 0.4 + b * 0.3 + ring * 0.35) * swell
    rev_tail = reverb(x, rng, 1.8, 0.7, wet=0.5, brightness=3000)[:len(x)][::-1] * env_swell(dur, 0.7, 2.0, 0.6) * 0.5
    return reverb(mix((x, 1.0), (rev_tail, 1.0)), rng, 1.5, 0.5, wet=0.3, brightness=4000)


def big_cloak(rng, v):
    """A chakra cloak or Susanoo forming: a rising, roaring surge that bursts into being."""
    p = vary(rng, 0.05)
    dur = 2.3
    n = samples(dur)
    rise_t = 1.3
    f = np.concatenate([glide(45 * p, 110 * p, rise_t), np.full(n - samples(rise_t), 110 * p)])
    growl = saw(f, harmonics=30) * (0.7 + 0.3 * np.abs(smooth_random(dur, rng, 25)))
    growl = lowpass(saturate(growl * 0.8, 2.5), 1800)
    surge = sweep_filter(pink(dur, rng), np.concatenate([np.geomspace(200, 2500, samples(rise_t)),
                                                         np.geomspace(2500, 400, n - samples(rise_t))]), q=1.0)
    shape = np.concatenate([np.linspace(0, 1, samples(rise_t)) ** 2, np.exp(-np.arange(n - samples(rise_t)) / (0.35 * SR))])
    burst = np.zeros(n)
    place(burst, thump(90 * p, 35 * p, 0.8, 0.2), samples(rise_t), 1.0)
    x = mix((growl * shape, 0.5), (surge * shape, 0.6), (burst, 0.9))
    return reverb(saturate(x, 2.0), rng, 1.6, 0.45, wet=0.25, brightness=3500)


def big_sharingan(rng, v):
    """The Sharingan opening: a sharp, piercing metallic ring with a quick 'shing'."""
    p = vary(rng, 0.03)
    dur = 1.4
    f = 2350 * p
    ring = modal([f, f * 1.51, f * 2.07, f * 2.93], [0.5, 0.35, 0.22, 0.12], [1, 0.6, 0.4, 0.2], dur, rng)
    shing = sweep_filter(white(0.25, rng), np.geomspace(3000, 9000, samples(0.25)), q=4) * env_ad(0.25, 0.005, 0.05)
    low = sine(f / 8, dur) * env_ad(dur, 0.002, 0.3) * 0.3
    return reverb(mix((ring, 0.45), (shing, 0.5), (low, 1.0), (click(rng, 0.004, 4000, 14000), 0.5)),
                  rng, 1.2, 0.35, wet=0.25, brightness=9000)


# ------------------------------------------------------------------ UI (quiet, minimal, ink and paper)

def ui_open(rng, v):
    """A screen opening: a soft paper slide rising, with a faint low tone."""
    dur = 0.28
    paper = sweep_filter(white(dur, rng), np.geomspace(700, 3500, samples(dur)), q=1.4) * env_points(dur, [(0, 0), (0.12, 1), (dur, 0)])
    tone = sine(glide(392, 523, dur)) * env_ad(dur, 0.02, 0.07) * 0.25
    return mix((paper, 0.5), (tone, 1.0))


def ui_close(rng, v):
    """A screen closing: the same slide, falling and a little shorter."""
    dur = 0.22
    paper = sweep_filter(white(dur, rng), np.geomspace(3000, 600, samples(dur)), q=1.4) * env_points(dur, [(0, 0), (0.05, 1), (dur, 0)])
    tone = sine(glide(440, 330, dur)) * env_ad(dur, 0.01, 0.05) * 0.2
    return mix((paper, 0.5), (tone, 1.0))


def ui_hover(rng, v):
    """Hovering a button: the tiniest wooden tick."""
    p = vary(rng, 0.05)
    dur = 0.05
    tick = modal([2400 * p, 4100 * p], [0.008, 0.004], [1, 0.4], dur, rng)
    return mix((tick, 0.6), (click(rng, 0.003, 3000, 9000), 0.15))


def ui_click(rng, v):
    """A button press: a small, firm wooden knock."""
    p = vary(rng, 0.04)
    dur = 0.1
    knock = modal([1180 * p, 2900 * p, 4600 * p], [0.02, 0.01, 0.005], [1, 0.5, 0.25], dur, rng)
    return mix((knock, 0.8), (click(rng, 0.004, 2000, 9000), 0.35), (thump(240, 160, 0.04, 0.008), 0.3))


def ui_tab(rng, v):
    """Switching tabs: a short, soft slide and tick."""
    dur = 0.13
    slide = bandpass(white(dur, rng), 1500, 5000) * env_points(dur, [(0, 0), (0.04, 1), (dur, 0)])
    tick = modal([1600, 3300], [0.012, 0.006], [1, 0.4], dur, rng)
    return mix((slide, 0.25), (tick, 0.5))


def _taiko(rng, f=70):
    dur = 0.9
    return mix((thump(f * 1.6, f, dur, 0.18), 1.0), (lowpass(white(0.08, rng), 1200) * env_ad(0.08, 0.001, 0.015), 0.4))


def ui_rank_up(rng, v):
    """A rank-up / advancement sting: two taiko hits and a rising chord crowned by a bell."""
    dur = 2.8
    x = np.zeros(samples(dur))
    place(x, _taiko(rng, 65), 0, 0.9)
    place(x, _taiko(rng, 82), samples(0.22), 1.0)
    notes = [(220, 0.42), (277.2, 0.52), (329.6, 0.62), (440, 0.72)]
    for f, at in notes:
        tone = (saw(np.full(samples(1.8), f), harmonics=6) * 0.5 + sine(f, 1.8)) * env_ad(1.8, 0.01, 0.6)
        place(x, lowpass(tone, 2200), samples(at), 0.22)
    bell = fm(880, 3.5, 1.5, 1.8, env_ad(1.8, 0.002, 0.2)) * env_ad(1.8, 0.003, 0.6)
    place(x, bell, samples(0.72), 0.35)
    return reverb(x, rng, 1.6, 0.5, wet=0.25, brightness=6000)


def ui_mission_complete(rng, v):
    """A mission complete: one taiko, then a calm two-note resolution and a soft chime."""
    dur = 2.4
    x = np.zeros(samples(dur))
    place(x, _taiko(rng, 72), 0, 0.85)
    for f, at in ((293.7, 0.18), (392, 0.45)):
        tone = (sine(f, 1.6) + 0.4 * sine(f * 2, 1.6) + 0.2 * sine(f * 1.5, 1.6)) * env_ad(1.6, 0.01, 0.5)
        place(x, tone, samples(at), 0.3)
    bell = fm(1174.7, 3.5, 1.0, 1.6, env_ad(1.6, 0.002, 0.2)) * env_ad(1.6, 0.003, 0.5)
    place(x, bell, samples(0.6), 0.25)
    return reverb(x, rng, 1.6, 0.5, wet=0.28, brightness=6000)


# ------------------------------------------------------------------ ambience (seamless loops)

def _bird(rng):
    """A distant little bird: two or three quick pitch-swept chirps."""
    out = np.zeros(samples(0.5))
    at = 0.0
    base = rng.uniform(2800, 4200)
    for _ in range(rng.integers(2, 4)):
        d = rng.uniform(0.04, 0.08)
        f = glide(base * rng.uniform(0.9, 1.1), base * rng.uniform(1.2, 1.5), d)
        place(out, sine(f * (1 + 0.03 * np.sin(2 * np.pi * 60 * t_axis(d)))) * env_ad(d, 0.005, d / 3), samples(at))
        at += d + rng.uniform(0.03, 0.08)
    return out


def _knock(rng):
    """A far-off hammer on wood."""
    f = rng.uniform(300, 520)
    return modal([f, f * 2.3, f * 3.9], [0.05, 0.025, 0.012], [1, 0.5, 0.25], 0.25, rng)


def _wind_chime(rng):
    f = rng.choice([1318.5, 1568, 1760, 2093])
    return fm(f, 2.76, 0.8, 1.5, env_ad(1.5, 0.002, 0.2)) * env_ad(1.5, 0.002, 0.5)


def ambient_village(rng, v):
    """A village on a calm day (a 24 s loop): soft wind, leaves, distant birds, a hammer, a chime."""
    dur = 24.0
    t = t_axis(dur)
    wind_env = 0.55 + 0.45 * (0.5 + 0.5 * np.sin(2 * np.pi * (3 / dur) * t + rng.uniform(0, 6)))
    wind = bandpass(pink(dur, rng, circular=True), 180, 900, circular=True) * wind_env
    leaves = bandpass(white(dur, rng), 2500, 7000, circular=True) * wind_env ** 3 * 0.12
    x = wind * 0.5 + leaves
    for _ in range(int(rng.integers(5, 8))):
        place(x, lowpass(_bird(rng), 6000), samples(rng.uniform(0, dur)), rng.uniform(0.03, 0.07), wrap=True)
    knock_at = rng.uniform(0, dur)
    for k in range(int(rng.integers(3, 6))):
        place(x, _knock(rng), samples(knock_at + k * rng.uniform(0.45, 0.6)), 0.05, wrap=True)
    for _ in range(3):
        place(x, _wind_chime(rng), samples(rng.uniform(0, dur)), 0.025, wrap=True)
    x = reverb(x, rng, 2.0, 0.6, wet=0.35, brightness=4000, circular=True)
    return loopify(highpass(x, 40, circular=True))


def ambient_sanctuary(rng, v):
    """The sage sanctuary (a 20 s loop): a deep still drone, falling water and stone chimes."""
    dur = 20.0
    t = t_axis(dur)
    drone = 0
    for f, a in ((55, 1.0), (82.5, 0.6), (110, 0.5), (165, 0.25)):
        fq = round(f * dur) / dur
        fb = round((f + 0.25) * dur) / dur
        drone = drone + a * (np.sin(2 * np.pi * fq * t) + 0.6 * np.sin(2 * np.pi * fb * t))
    breath = 0.7 + 0.3 * np.sin(2 * np.pi * (2 / dur) * t)
    water = lowpass(pink(dur, rng, circular=True), 1400, circular=True) * (0.85 + 0.15 * np.sin(2 * np.pi * (5 / dur) * t))
    x = drone * breath * 0.12 + water * 0.18
    for _ in range(4):
        f = rng.choice([392, 523.3, 587.3, 784])
        stone = modal([f, f * 2.76, f * 5.4], [0.9, 0.4, 0.15], [1, 0.3, 0.1], 3.0, rng)
        place(x, stone, samples(rng.uniform(0, dur)), 0.05, wrap=True)
    x = reverb(x, rng, 3.0, 1.0, wet=0.4, brightness=3000, circular=True)
    return loopify(highpass(x, 30, circular=True))


# ------------------------------------------------------------------ the list
# event id -> (recipe, variants, peak dBFS, loop?)
# Peaks: hits and impacts loud (about -1 dB, like vanilla's), UI very quiet, ambience low.

SOUNDS = {
    "seal.clap": (seal_clap, 4, -4.0, False),
    "seal.complete": (seal_complete, 3, -3.0, False),
    "chakra.charge": (chakra_charge, 2, -6.0, True),
    "chakra.release": (chakra_release, 3, -1.0, False),
    "combat.hit_light": (hit_light, 4, -1.5, False),
    "combat.hit_heavy": (hit_heavy, 4, -1.0, False),
    "combat.guard": (guard, 3, -2.0, False),
    "combat.parry": (parry, 3, -1.5, False),
    "combat.just_frame": (just_frame, 2, -2.0, False),
    "combat.posture_break": (posture_break, 3, -1.0, False),
    "combat.swing_light": (swing_light, 4, -4.0, False),
    "combat.swing_heavy": (swing_heavy, 3, -3.0, False),
    "combat.body_thud": (body_thud, 3, -1.5, False),
    "combat.wall_splat": (wall_splat, 3, -1.0, False),
    "weapon.throw": (weapon_throw, 4, -4.0, False),
    "weapon.impact": (weapon_impact, 4, -2.0, False),
    "weapon.clash": (weapon_clash, 3, -1.5, False),
    "nature.fire_roar": (fire_roar, 3, -1.5, False),
    "nature.fire_crackle": (fire_crackle, 3, -3.0, False),
    "nature.fire_burst": (fire_burst, 3, -1.0, False),
    "nature.water_surge": (water_surge, 3, -1.5, False),
    "nature.water_splash": (water_splash, 3, -1.5, False),
    "nature.lightning_crackle": (lightning_crackle, 2, -4.0, True),
    "nature.lightning_snap": (lightning_snap, 4, -1.0, False),
    "nature.wind_gust": (wind_gust, 3, -2.0, False),
    "nature.wind_whistle": (wind_whistle, 3, -2.0, False),
    "nature.earth_rumble": (earth_rumble, 3, -1.5, False),
    "nature.earth_crack": (earth_crack, 3, -1.0, False),
    "nature.ice_shatter": (ice_shatter, 3, -2.0, False),
    "nature.lava_hiss": (lava_hiss, 3, -2.0, False),
    "nature.boil_steam": (boil_steam, 3, -2.0, False),
    "nature.storm_hum": (storm_hum, 2, -3.0, True),
    "nature.explosion_boom": (explosion_boom, 3, -0.8, False),
    "nature.scorch_sizzle": (scorch_sizzle, 3, -3.0, False),
    "nature.dust_hum": (dust_hum, 2, -2.5, False),
    "nature.magnet_hiss": (magnet_hiss, 3, -2.5, False),
    "nature.wood_creak": (wood_creak, 3, -2.0, False),
    "nature.wood_burst": (wood_burst, 3, -1.0, False),
    "nature.yin_swell": (yin_swell, 2, -2.0, False),
    "nature.yang_swell": (yang_swell, 2, -2.5, False),
    "nature.medical_chime": (medical_chime, 3, -4.0, False),
    "big.impact": (big_impact, 3, -0.5, False),
    "big.genjutsu": (big_genjutsu, 2, -2.5, False),
    "big.cloak": (big_cloak, 2, -1.0, False),
    "big.sharingan": (big_sharingan, 2, -2.5, False),
    "ui.open": (ui_open, 2, -14.0, False),
    "ui.close": (ui_close, 2, -15.0, False),
    "ui.hover": (ui_hover, 3, -20.0, False),
    "ui.click": (ui_click, 3, -12.0, False),
    "ui.tab": (ui_tab, 2, -15.0, False),
    "ui.rank_up": (ui_rank_up, 2, -3.0, False),
    "ui.mission_complete": (ui_mission_complete, 2, -3.5, False),
    "ambient.village": (ambient_village, 2, -10.0, True),
    "ambient.sanctuary": (ambient_sanctuary, 2, -10.0, True),
}
