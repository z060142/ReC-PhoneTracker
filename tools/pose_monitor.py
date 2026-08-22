#!/usr/bin/env python3
"""Phase-1 UDP validation instrument for Phone 6DoF Tracker."""
import argparse, csv, os, select, socket, statistics, struct, sys, time
from collections import deque

POSE_FMT = '<IHHIIQQ7fBBBB'
CONTROL_FMT = '<IHHIQII'
LENS_FMT = '<IHHI4fI'
POSE_MAGIC, CONTROL_MAGIC, LENS_MAGIC = 0x54443350, 0x54433350, 0x534E4C50
assert struct.calcsize(POSE_FMT) == 64
assert struct.calcsize(CONTROL_FMT) == 28
assert struct.calcsize(LENS_FMT) == 32

def arcore_to_cry(px, py, pz, qx, qy, qz, qw): return (px, -pz, py), (qx, -qz, qy, qw)
def percentile(values, p):
    if not values: return 0.0
    values = sorted(values); return values[round((len(values)-1)*p)]

def control(cmd, seq=0, pc_ns=0, param=0): return struct.pack(CONTROL_FMT, CONTROL_MAGIC, 1, cmd, seq, pc_ns, param, 0)
def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--port',type=int,default=9050); ap.add_argument('--log'); ap.add_argument('--plot',action='store_true'); args=ap.parse_args()
    s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); s.bind(('',args.port)); s.setblocking(False)
    out=None; writer=None
    if args.log:
        out=open(args.log,'w',newline='',encoding='utf-8'); writer=csv.writer(out); writer.writerow(['t_recv_ns','magic','version','flags','seq','origin_epoch','t_device_ns','t_frame_ns','px','py','pz','qx','qy','qz','qw','tracking_state','failure_reason','battery_pct','app_fps'])
    packets=0; lost=0; previous_seq=None; previous_at=None; intervals=deque(maxlen=6000); last=None; peer=None; ping_seq=0; pings={}; last_hud=0
    print(f'Listening UDP :{args.port}; commands: r=recenter p=ping q=quit')
    try:
        while True:
            # Windows select() only supports sockets. Use msvcrt for an interactive
            # console; a redirected/background monitor remains receive-only.
            key = None
            if os.name == 'nt':
                if sys.stdin.isatty():
                    import msvcrt
                    if msvcrt.kbhit(): key = msvcrt.getwch().strip().lower()
                readable, _, _ = select.select([s], [], [], 0.1)
            else:
                watch = [s] + ([sys.stdin] if sys.stdin.isatty() else [])
                readable, _, _ = select.select(watch, [], [], 0.1)
                if sys.stdin in readable: key = sys.stdin.readline().strip().lower()
            if key:
                if key=='q': break
                if peer and key=='r': s.sendto(control(3),peer); print('RECENTER sent')
                if peer and key=='p': ping_seq+=1; t=time.monotonic_ns(); pings[ping_seq]=t; s.sendto(control(1,ping_seq,t),peer)
            if s in readable:
                data,peer=s.recvfrom(256); now=time.monotonic_ns()
                if len(data)==28:
                    magic,ver,cmd,seq,tpc,param,res=struct.unpack(CONTROL_FMT,data)
                    if magic==CONTROL_MAGIC and cmd==2 and seq in pings: print(f'PONG seq={seq} RTT={(now-pings.pop(seq))/1e6:.3f} ms')
                    continue
                if len(data)==32:
                    magic,ver,flags,seq,focal,aperture,focus,ev,iso=struct.unpack(LENS_FMT,data)
                    if magic==LENS_MAGIC and ver==1: print(f'LENS seq={seq} {focal:.0f}mm f/{aperture:.1f} focus={focus:.2f}m EV{ev:+.0f} ISO{iso} dof={bool(flags&1)} exposure={bool(flags&2)}')
                    continue
                if len(data)!=64: continue
                row=struct.unpack(POSE_FMT,data)
                if row[0]!=POSE_MAGIC or row[1]!=1: continue
                packets+=1; seq=row[3]
                if previous_seq is not None and seq>previous_seq+1: lost+=seq-previous_seq-1
                previous_seq=seq
                if previous_at: intervals.append((now-previous_at)/1e6)
                previous_at=now; last=row
                if writer: writer.writerow([now,*row]); out.flush()
            if time.monotonic()-last_hud>=1 and last:
                last_hud=time.monotonic(); px,py,pz,qx,qy,qz,qw=last[7:14]; cp,cq=arcore_to_cry(px,py,pz,qx,qy,qz,qw); a=list(intervals); loss=100*lost/max(1,packets+lost)
                print(f'{packets:6d} pkt  {len(a):.1f} Hz window  loss {loss:.3f}%  interval ms mean={statistics.fmean(a) if a else 0:.2f} p50={percentile(a,.50):.2f} p95={percentile(a,.95):.2f} p99={percentile(a,.99):.2f} max={max(a,default=0):.2f}')
                print(f'raw ARCore  p=({px:+.3f},{py:+.3f},{pz:+.3f}) q=({qx:+.3f},{qy:+.3f},{qz:+.3f},{qw:+.3f}) | Cry p=({cp[0]:+.3f},{cp[1]:+.3f},{cp[2]:+.3f}) q=({cq[0]:+.3f},{cq[1]:+.3f},{cq[2]:+.3f},{cq[3]:+.3f}) state={last[14]} battery={last[16]}% app={last[17]}fps')
    finally:
        if out: out.close()
if __name__=='__main__': main()
