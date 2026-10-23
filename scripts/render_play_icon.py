#!/usr/bin/env python3
"""Play 스토어 아이콘(512×512 PNG)을 앱 아이콘과 같은 도형으로 그린다.

도형 좌표는 app/src/main/res/drawable/ic_launcher_{background,foreground}.xml(108 단위)과 같다.
한쪽을 고치면 다른 쪽도 고친다. 사용: python3 scripts/render_play_icon.py [출력 경로]
"""
import sys

from PIL import Image, ImageDraw

SIZE = 512
SUPERSAMPLE = 4
NAVY = (0x0B, 0x15, 0x30)
BODY = (0x1B, 0x2A, 0x52)
BRIGHT = (0x4D, 0xD8, 0xFF)


def render(size: int) -> Image.Image:
    big = size * SUPERSAMPLE
    s = big / 108
    image = Image.new("RGB", (big, big), NAVY)
    draw = ImageDraw.Draw(image)

    def p(x, y):
        return (x * s, y * s)

    # 본체 (39..69 × 24..84, 반경 12) — 배경보다 조금 밝게
    draw.rounded_rectangle([p(39, 24), p(69, 84)], radius=12 * s, fill=BODY)
    # 올리기 · 내리기 — 이것만 밝게
    draw.polygon([p(54, 36), p(63, 48), p(45, 48)], fill=BRIGHT)
    draw.polygon([p(45, 60), p(63, 60), p(54, 72)], fill=BRIGHT)

    return image.resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "docs/play/assets/icon-512.png"
    render(SIZE).save(out)
    print(out)
