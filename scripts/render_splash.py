#!/usr/bin/env python3
"""시스템 스플래시 화면 가운데 그림(머리판을 올린 침대 + 그 아래 LINAK·일룸 로고)을 그린다.

108 단위 정사각형에 그리고, Android 12+ 스플래시 규격대로 가운데 지름 72 원 안에만 내용을 둔다.
- 침대: 홈 화면 BedIllustration(feature/home)과 같은 비율·색, 머리판 30° 올림(시안)
- 로고: scripts/assets/의 받은 원본(흰 바탕)을 여백만 잘라, 원래 색 그대로 흰 둥근 판 위에 나란히.
  네이비 배경에서 검정·빨강 로고가 보이게 하려는 것이고 로고 색은 바꾸지 않는다

사용: python3 scripts/render_splash.py [출력 경로]  (기본 app/src/main/res/drawable-nodpi/splash_icon.webp)
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
LOGOS = [ROOT / "scripts/assets/linak-logo.webp", ROOT / "scripts/assets/iloom-logo.png"]
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
BED_TOP = 49.5  # 매트리스 윗면 — 침대만 그릴 때 위아래 가운데
BED_TOP_WITH_LOGOS = 43.5  # 로고 판까지 합쳐 위아래 가운데
THICK = 6
GAP = 0.4
HEAD_DEG = 30
# 로고 판 (침대 다리 아래)
PLATE_GAP = 4  # 침대 다리 끝 ~ 판 윗변
PLATE_HEIGHT = 8
PLATE_PAD = 3
LOGO_HEIGHTS = [4.4, 3.4]  # LINAK, 일룸 — 글자 높이가 비슷해 보이게
LOGO_GAP = 2.5  # 로고 ~ 구분선
DIVIDER = (0xC8, 0xCD, 0xD8)


def render(with_logos: bool = True) -> Image.Image:
    big = SIZE * SUPERSAMPLE
    s = big / 108
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))

    def box(x, y, w, h):
        return [x * s, y * s, (x + w) * s, (y + h) * s]

    def rr(draw, x, y, w, h, r, fill):
        draw.rounded_rectangle(box(x, y, w, h), radius=r * s, fill=fill)

    bed_top = BED_TOP_WITH_LOGOS if with_logos else BED_TOP
    bottom = bed_top + THICK
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
    rr(draw, seat_left + GAP, bed_top, SEAT - 2 * GAP, THICK, 2, MATTRESS)
    rr(draw, seat_right + GAP, bed_top, FOOT - GAP, THICK, 2, MATTRESS)
    # 올라간 머리판 + 베개: 엉덩이판 왼쪽 아래 모서리를 축으로 화면 시계 방향 30°
    head = Image.new("RGBA", image.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(head)
    rr(hd, LEFT, bed_top, HEAD - GAP, THICK, 2, CYAN)
    rr(hd, LEFT + HEAD * 0.08, bed_top - 3, HEAD * 0.42, 3.6, 1.8, PILLOW)
    head = head.rotate(-HEAD_DEG, resample=Image.BICUBIC, center=(seat_left * s, bottom * s))
    image.alpha_composite(head)
    if with_logos:
        _draw_logo_plate(image, s, frame_top + 12 + PLATE_GAP)

    return image.resize((SIZE, SIZE), Image.LANCZOS)


def _logo(path: Path, height_px: int) -> Image.Image:
    """흰 바탕 원본에서 로고 부분만 잘라 높이를 맞춘다."""
    src = Image.open(path).convert("RGB")
    diff = ImageChops.difference(src, Image.new("RGB", src.size, (255, 255, 255))).convert("L")
    src = src.crop(diff.point(lambda v: 255 if v > 24 else 0).getbbox())
    return src.resize((round(src.width * height_px / src.height), height_px), Image.LANCZOS)


def _draw_logo_plate(image: Image.Image, s: float, top: float) -> None:
    logos = [_logo(p, round(h * s)) for p, h in zip(LOGOS, LOGO_HEIGHTS)]
    inner = sum(l.width for l in logos) / s + 2 * LOGO_GAP
    width = inner + 2 * PLATE_PAD
    left = 54 - width / 2
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle(
        [left * s, top * s, (left + width) * s, (top + PLATE_HEIGHT) * s], radius=PLATE_HEIGHT / 2 * s, fill="white"
    )
    center_y = top + PLATE_HEIGHT / 2
    x = left + PLATE_PAD
    linak, iloom = logos
    image.paste(linak, (round(x * s), round(center_y * s - linak.height / 2)))
    x += linak.width / s + LOGO_GAP
    draw.line([x * s, (center_y - 2.4) * s, x * s, (center_y + 2.4) * s], fill=DIVIDER, width=max(1, round(0.25 * s)))
    x += LOGO_GAP
    image.paste(iloom, (round(x * s), round(center_y * s - iloom.height / 2)))


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    render().save(out, lossless=True, method=6)
    print(out)
