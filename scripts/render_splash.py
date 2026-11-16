#!/usr/bin/env python3
"""시스템 스플래시 화면 가운데 그림(머리판을 올린 침대)을 그린다.

108 단위 정사각형에 그리고, Android 12+ 스플래시 규격대로 가운데 지름 72 원 안에만 내용을 둔다.
침대는 홈 화면 BedIllustration(feature/home)과 같은 비율·색, 머리판 30° 올림(시안).

사용: python3 scripts/render_splash.py [출력 경로]  (기본 app/src/main/res/drawable-nodpi/splash_icon.webp)
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/res/drawable-nodpi/splash_icon.webp"

SIZE = 1080  # 108 단위 × 10px — 주요 기기에서 원본보다 크게 늘어나지 않게
SUPERSAMPLE = 3
FRAME = (0x2E, 0x42, 0x75)
MATTRESS = (0x6A, 0x82, 0xC2)
CYAN = (0x4D, 0xD8, 0xFF)
PILLOW = (0xD5, 0xDF, 0xF7)

# 침대 (BedIllustration Geometry 비율, 길이 60 단위)
LEFT, LENGTH = 24, 60
HEAD, SEAT, FOOT = LENGTH * 0.36, LENGTH * 0.24, LENGTH * 0.40
BED_TOP = 49.5  # 매트리스 윗면 (침대 전체가 위아래 가운데에 오게)
THICK = 6
GAP = 0.4
HEAD_DEG = 30


def render() -> Image.Image:
    big = SIZE * SUPERSAMPLE
    s = big / 108
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))

    def box(x, y, w, h):
        return [x * s, y * s, (x + w) * s, (y + h) * s]

    def rr(draw, x, y, w, h, r, fill):
        draw.rounded_rectangle(box(x, y, w, h), radius=r * s, fill=fill)

    bottom = BED_TOP + THICK
    frame_top = bottom + 2
    seat_left = LEFT + HEAD
    seat_right = seat_left + SEAT

    draw = ImageDraw.Draw(image)
    # 다리 · 프레임
    for x in (LEFT + LENGTH * 0.06, LEFT + LENGTH * 0.94 - 2.4):
        rr(draw, x, frame_top, 2.4, 12, 1.2, FRAME)
    rr(draw, LEFT, frame_top, LENGTH, 4, 2, FRAME)
    # 머리판 지지대
    a = math.radians(HEAD_DEG)
    reach = -HEAD * 0.55
    end = (seat_left + reach * math.cos(a), bottom - abs(reach) * math.sin(a))
    start = (seat_left + reach * 0.6, frame_top)
    draw.line([start[0] * s, start[1] * s, end[0] * s, end[1] * s], fill=FRAME, width=round(1.3 * s))
    # 엉덩이 · 발 매트리스
    rr(draw, seat_left + GAP, BED_TOP, SEAT - 2 * GAP, THICK, 2, MATTRESS)
    rr(draw, seat_right + GAP, BED_TOP, FOOT - GAP, THICK, 2, MATTRESS)
    # 올라간 머리판 + 베개: 엉덩이판 왼쪽 아래 모서리를 축으로 화면 시계 방향 30°
    head = Image.new("RGBA", image.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(head)
    rr(hd, LEFT, BED_TOP, HEAD - GAP, THICK, 2, CYAN)
    rr(hd, LEFT + HEAD * 0.08, BED_TOP - 3, HEAD * 0.42, 3.6, 1.8, PILLOW)
    head = head.rotate(-HEAD_DEG, resample=Image.BICUBIC, center=(seat_left * s, bottom * s))
    image.alpha_composite(head)

    return image.resize((SIZE, SIZE), Image.LANCZOS)


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    render().save(out, lossless=True, method=6)
    print(out)
