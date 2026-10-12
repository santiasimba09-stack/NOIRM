"""Send key presses / mouse clicks to an X display via XTest. Usage: xinput.py :99 key r | click 1 | hold key v 1.5 | move x y"""
import ctypes, sys, time
x11 = ctypes.cdll.LoadLibrary("libX11.so.6"); xtst = ctypes.cdll.LoadLibrary("libXtst.so.6")
x11.XOpenDisplay.restype = ctypes.c_void_p
x11.XStringToKeysym.restype = ctypes.c_ulong
x11.XStringToKeysym.argtypes = [ctypes.c_char_p]
x11.XKeysymToKeycode.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
xtst.XTestFakeKeyEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
xtst.XTestFakeButtonEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
xtst.XTestFakeMotionEvent.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_ulong]
x11.XFlush.argtypes = [ctypes.c_void_p]
d = x11.XOpenDisplay(sys.argv[1].encode())
assert d, "cannot open display"
def key(name, down):
    code = x11.XKeysymToKeycode(d, x11.XStringToKeysym(name.encode()))
    xtst.XTestFakeKeyEvent(d, code, down, 0); x11.XFlush(d)
def button(b, down):
    xtst.XTestFakeButtonEvent(d, b, down, 0); x11.XFlush(d)
args = sys.argv[2:]
while args:
    cmd = args.pop(0)
    if cmd == "key":
        k = args.pop(0); key(k, 1); time.sleep(0.08); key(k, 0)
    elif cmd == "hold":
        k = args.pop(0); t = float(args.pop(0)); key(k, 1); time.sleep(t); key(k, 0)
    elif cmd == "click":
        b = int(args.pop(0)); button(b, 1); time.sleep(0.06); button(b, 0)
    elif cmd == "move":
        xtst.XTestFakeMotionEvent(d, -1, int(args.pop(0)), int(args.pop(0)), 0); x11.XFlush(d)
    elif cmd == "sleep":
        time.sleep(float(args.pop(0)))
    time.sleep(0.05)
