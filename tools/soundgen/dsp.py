"""Small synthesis toolkit for NOIRM's sounds: everything is made from noise, oscillators and maths.

No audio is ever loaded from anywhere. Every building block here is a few lines of numpy:

* sources: white/pink/brown noise, sine, saw, square, FM, impulses, bubbles, modal "struck metal";
* shaping: envelopes, fades, saturation, pitch/time stretch by resampling;
* filters: FFT filters (zero-phase low/high/band-pass, also time-varying via short frames), a
  one-pole IIR low-pass and a resonant biquad (classic "cookbook" formulas, written out by hand);
* space: convolution with a synthetic room impulse response (decaying filtered noise).

FFT filtering treats the buffer as circular, which is exactly what we want for loops: a loop
filtered this way joins its own start without a click.
"""
import numpy as np

SR = 44100


def secs(n):
    return n / SR


def samples(seconds):
    return max(1, int(round(seconds * SR)))


def t_axis(seconds):
    return np.arange(samples(seconds)) / SR


# ------------------------------------------------------------------ sources

def white(seconds, rng):
    return rng.standard_normal(samples(seconds))


def colored(seconds, rng, slope_db_per_oct, circular=False):
    """Noise with a spectral tilt: -3 dB/oct is pink, -6 dB/oct is brown."""
    n = samples(seconds)
    pad = 0 if circular else n // 4
    spec = np.fft.rfft(rng.standard_normal(n + pad))
    f = np.fft.rfftfreq(n + pad, 1 / SR)
    f[0] = f[1]
    spec *= (f / 1000.0) ** (slope_db_per_oct / 6.0206)
    out = np.fft.irfft(spec, n + pad)[:n]
    return out / (np.std(out) + 1e-12)


def pink(seconds, rng, circular=False):
    return colored(seconds, rng, -3, circular)


def brown(seconds, rng, circular=False):
    return colored(seconds, rng, -6, circular)


def phase_from_freq(freq):
    """Freq may be a number or an array (a glide): returns the running phase in radians."""
    freq = np.asarray(freq, dtype=float)
    return 2 * np.pi * np.cumsum(freq) / SR


def sine(freq, seconds=None, phase0=0.0):
    if np.ndim(freq) == 0:
        return np.sin(2 * np.pi * freq * t_axis(seconds) + phase0)
    return np.sin(phase_from_freq(freq) + phase0)


def saw(freq, seconds=None, harmonics=40):
    """Band-limited sawtooth from summed harmonics (no aliasing fizz)."""
    if np.ndim(freq) == 0:
        freq = np.full(samples(seconds), float(freq))
    ph = phase_from_freq(freq)
    out = np.zeros_like(ph)
    for k in range(1, harmonics + 1):
        mask = (k * freq) < SR * 0.45
        out += mask * np.sin(k * ph) / k
    return out * (2 / np.pi)


def square(freq, seconds=None, harmonics=30):
    if np.ndim(freq) == 0:
        freq = np.full(samples(seconds), float(freq))
    ph = phase_from_freq(freq)
    out = np.zeros_like(ph)
    for k in range(1, 2 * harmonics, 2):
        mask = (k * freq) < SR * 0.45
        out += mask * np.sin(k * ph) / k
    return out * (4 / np.pi)


def fm(carrier, ratio, index, seconds, index_env=None):
    """Two-operator FM: a modulator at carrier*ratio bends the carrier's phase."""
    t = t_axis(seconds)
    idx = index if index_env is None else index * index_env
    return np.sin(2 * np.pi * carrier * t + idx * np.sin(2 * np.pi * carrier * ratio * t))


def glide(f0, f1, seconds, curve="exp"):
    n = samples(seconds)
    x = np.linspace(0, 1, n)
    if curve == "exp":
        return f0 * (f1 / f0) ** x
    return f0 + (f1 - f0) * x


def modal(freqs, decays, amps, seconds, rng=None, jitter=0.0):
    """Struck object: a sum of decaying sine partials (metal, glass, wood, bells)."""
    t = t_axis(seconds)
    out = np.zeros_like(t)
    for f, d, a in zip(freqs, decays, amps):
        if f >= SR * 0.45:
            continue
        ph = 0.0 if rng is None else rng.uniform(0, 2 * np.pi)
        if jitter and rng is not None:
            f = f * (1 + rng.uniform(-jitter, jitter))
        out += a * np.exp(-t / d) * np.sin(2 * np.pi * f * t + ph)
    return out * tail_taper(len(t), 0.3)


def bubble(freq, seconds, rise=1.6, decay=0.03):
    """A single water bubble: a sine whose pitch rises as it decays (Minnaert resonance)."""
    t = t_axis(seconds)
    f = freq * (1 + (rise - 1) * np.clip(t / (decay * 4), 0, 1))
    return np.sin(phase_from_freq(f)) * np.exp(-t / decay) * tail_taper(len(t))


def place(dst, src, at_sample, gain=1.0, wrap=False):
    """Mixes src into dst starting at at_sample (wrapping round for loops)."""
    at_sample = int(at_sample)
    n = len(src)
    if wrap:
        idx = (np.arange(n) + at_sample) % len(dst)
        np.add.at(dst, idx, src * gain)
        return dst
    if at_sample >= len(dst):
        return dst
    end = min(len(dst), at_sample + n)
    if at_sample < 0:
        src = src[-at_sample:]
        at_sample = 0
        end = min(len(dst), len(src))
    dst[at_sample:end] += src[: end - at_sample] * gain
    return dst


def pad_to(x, seconds):
    n = samples(seconds)
    if len(x) >= n:
        return x[:n]
    return np.concatenate([x, np.zeros(n - len(x))])


# ------------------------------------------------------------------ envelopes

def env_ad(seconds, attack, decay_tau, hold=0.0):
    """Attack ramp, optional hold, then exponential decay with time constant decay_tau."""
    t = t_axis(seconds)
    a = np.clip(t / max(attack, 1e-5), 0, 1)
    a = np.sin(a * np.pi / 2) ** 2
    d = np.where(t < attack + hold, 1.0, np.exp(-(t - attack - hold) / decay_tau))
    return a * d * tail_taper(len(t))


def tail_taper(n, share=0.15):
    """A smooth fade over the last part, so nothing stops with a click when its buffer ends."""
    out = np.ones(n)
    k = max(2, int(n * share))
    out[-k:] = 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, k))
    return out


def env_points(seconds, points):
    """Piecewise-linear envelope through (time, level) points."""
    t = t_axis(seconds)
    ts, vs = zip(*points)
    return np.interp(t, ts, vs)


def env_swell(seconds, peak_at, power=2.0, release=0.05):
    """Rises slowly (power curve) to peak_at, then drops quickly: reverse-cymbal shape."""
    t = t_axis(seconds)
    up = np.clip(t / peak_at, 0, 1) ** power
    down = np.where(t <= peak_at, 1.0, np.exp(-(t - peak_at) / release))
    return up * down


def smooth_random(seconds, rng, rate_hz, circular=False):
    """A slowly wandering control signal in about [-1, 1] (low-passed noise)."""
    x = rng.standard_normal(samples(seconds))
    x = lowpass(x, rate_hz, order=2, circular=circular)
    return x / (np.max(np.abs(x)) + 1e-12)


def fade(x, fade_in=0.002, fade_out=0.01):
    x = x.copy()
    a, b = samples(fade_in), samples(fade_out)
    if a > 1:
        x[:a] *= np.linspace(0, 1, a)
    if b > 1:
        x[-b:] *= np.linspace(1, 0, b)
    return x


# ------------------------------------------------------------------ filters

def _fft_apply(x, gain_fn, circular):
    n = len(x)
    pad = 0 if circular else min(n, SR // 2)
    buf = np.concatenate([x, np.zeros(pad)]) if pad else x
    spec = np.fft.rfft(buf)
    f = np.fft.rfftfreq(len(buf), 1 / SR)
    out = np.fft.irfft(spec * gain_fn(f), len(buf))
    return out[:n]


def _lp_gain(f, fc, order):
    return 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def _hp_gain(f, fc, order):
    f = np.maximum(f, 1e-3)
    return 1.0 / np.sqrt(1.0 + (fc / f) ** (2 * order))


def lowpass(x, fc, order=2, circular=False):
    return _fft_apply(x, lambda f: _lp_gain(f, fc, order), circular)


def highpass(x, fc, order=2, circular=False):
    return _fft_apply(x, lambda f: _hp_gain(f, fc, order), circular)


def bandpass(x, lo, hi, order=2, circular=False):
    return _fft_apply(x, lambda f: _lp_gain(f, hi, order) * _hp_gain(f, lo, order), circular)


def peak(x, fc, q, gain_db, circular=False):
    """A bell-shaped boost/cut around fc (FFT version)."""
    g = 10 ** (gain_db / 20)

    def fn(f):
        bw = fc / q
        return 1 + (g - 1) * np.exp(-0.5 * ((f - fc) / (bw / 2)) ** 2)
    return _fft_apply(x, fn, circular)


def tilt(x, db_per_oct, circular=False):
    def fn(f):
        f = np.maximum(f, 20)
        return (f / 1000) ** (db_per_oct / 6.0206)
    return _fft_apply(x, fn, circular)


def sweep_filter(x, centers, q=2.0, kind="band", frame=1024):
    """Time-varying filter: short overlapping frames, each filtered around its own centre
    frequency (centers: one value per sample). Gives whooshes, wah and swept noise."""
    n = len(x)
    hop = frame // 4
    win = np.hanning(frame)
    padded = np.concatenate([np.zeros(frame), x, np.zeros(frame)])
    cpad = np.concatenate([np.full(frame, centers[0]), centers, np.full(frame, centers[-1])])
    out = np.zeros_like(padded)
    norm = np.zeros_like(padded)
    f = np.fft.rfftfreq(frame, 1 / SR)
    for start in range(0, len(padded) - frame, hop):
        seg = padded[start:start + frame] * win
        fc = max(20.0, float(cpad[start + frame // 2]))
        if kind == "band":
            bw = fc / q
            g = np.exp(-0.5 * ((f - fc) / (bw / 2)) ** 2)
        elif kind == "low":
            g = _lp_gain(f, fc, 2)
        else:
            g = _hp_gain(f, fc, 2)
        out[start:start + frame] += np.fft.irfft(np.fft.rfft(seg) * g, frame) * win
        norm[start:start + frame] += win ** 2
    out = out / np.maximum(norm, 1e-6)
    return out[frame:frame + n]


def onepole_lowpass(x, fc):
    """A classic one-pole IIR low-pass, y[n] = y[n-1] + a (x[n] - y[n-1])."""
    a = 1 - np.exp(-2 * np.pi * fc / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc)
        y[i] = acc
    return y


def biquad_bandpass(x, fc, q):
    """RBJ cookbook resonant band-pass (constant peak gain), run sample by sample."""
    w0 = 2 * np.pi * fc / SR
    alpha = np.sin(w0) / (2 * q)
    b0, b1, b2 = alpha, 0.0, -alpha
    a0, a1, a2 = 1 + alpha, -2 * np.cos(w0), 1 - alpha
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, v in enumerate(x):
        out = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, v, y1, out
        y[i] = out
    return y


def comb(x, delay_s, feedback, circular=False):
    """Feed-forward comb (a metallic tint): x + fb * x delayed, applied in the FFT domain."""
    d = delay_s

    def fn(f):
        return np.abs(1 + feedback * np.exp(-2j * np.pi * f * d))
    return _fft_apply(x, fn, circular)


# ------------------------------------------------------------------ shaping and space

def saturate(x, drive=2.0):
    """Soft clipping: adds weight and glues layers together (tanh)."""
    return np.tanh(drive * x) / np.tanh(drive)


def resample(x, factor):
    """Plays x 'factor' times faster (pitch up, shorter) by linear interpolation."""
    n = int(len(x) / factor)
    src = np.arange(n) * factor
    return np.interp(src, np.arange(len(x)), x)


def room_ir(seconds, rng, decay, brightness=4000, predelay=0.0):
    """A synthetic room: decaying noise, darker as it fades (high frequencies die first)."""
    n = samples(seconds)
    t = np.arange(n) / SR
    early = rng.standard_normal(n) * np.exp(-t / decay)
    dark = lowpass(early, brightness * 0.25, order=1)
    mix = np.clip(t / seconds, 0, 1)
    ir = lowpass(early, brightness, order=1) * (1 - mix) + dark * mix
    if predelay > 0:
        ir = np.concatenate([np.zeros(samples(predelay)), ir])
    ir[0] += 0.0
    return ir / (np.sqrt(np.sum(ir ** 2)) + 1e-12)


def convolve(x, ir, circular=False):
    if circular:
        n = len(x)
        h = np.zeros(n)
        m = min(n, len(ir))
        h[:m] = ir[:m]
        return np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(h), n)
    n = len(x) + len(ir) - 1
    size = 1 << (n - 1).bit_length()
    out = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:n]
    return out


def reverb(x, rng, seconds=1.0, decay=0.3, wet=0.25, brightness=5000, tail=True, circular=False):
    ir = room_ir(seconds, rng, decay, brightness)
    if not circular:
        x = x * tail_taper(len(x), 0.05)
    if circular:
        w = convolve(x, ir, circular=True)
        return x * (1 - wet) + w * wet * (np.std(x) / (np.std(w) + 1e-12))
    w = convolve(x, ir)
    dry = np.concatenate([x, np.zeros(len(w) - len(x))]) if tail else x
    if not tail:
        w = w[:len(x)]
    scale = np.max(np.abs(x)) / (np.max(np.abs(w)) + 1e-12)
    return dry * (1 - wet) + w * wet * scale


def norm(x, peak_db=-1.0):
    m = np.max(np.abs(x))
    if m < 1e-12:
        return x
    return x / m * (10 ** (peak_db / 20))


def remove_dc(x, circular=False):
    return highpass(x - np.mean(x), 18, order=2, circular=circular)


def trim_silence(x, threshold_db=-70, keep=0.02):
    """Cuts the inaudible end of a tail (keeps files small)."""
    thr = 10 ** (threshold_db / 20) * np.max(np.abs(x))
    idx = np.nonzero(np.abs(x) > thr)[0]
    if len(idx) == 0:
        return x
    end = min(len(x), idx[-1] + samples(keep))
    return x[:end]


def mix(*layers):
    """Sums layers of different lengths (each a (signal, gain) pair or a plain signal)."""
    items = [(l, 1.0) if not isinstance(l, tuple) else l for l in layers]
    n = max(len(s) for s, _ in items)
    out = np.zeros(n)
    for s, g in items:
        out[:len(s)] += s * g
    return out
