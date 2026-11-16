#!/usr/bin/env python3
"""Play 스토어 그래픽 이미지(1024×500)와 휴대전화 스크린샷(1080×1920)을 만든다.

- 그래픽 이미지: 네이비 바탕 + 앱 이름·설명 + 스플래시와 같은 침대 그림(scripts/render_splash.py)
- 스크린샷: store/raw/*.png(기기 캡처 1080×2340)에서 상태 바·내비게이션 바를 잘라 내고
  위에 설명 문구를 얹는다. Play는 긴 변이 짧은 변의 2배를 넘으면 안 되므로 9:16으로 맞춘다.
  원본 캡처는 debug 빌드의 가상 침대로 찍는다 (실제 침대 구동 없음).
글꼴: Noto Sans CJK KR (fonts-noto-cjk, OFL). 그림 안에 글자로만 들어가고 앱에는 포함되지 않는다.

사용: python3 scripts/render_play_assets.py  (출력: store/)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import render_splash  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "store"
RAW = ASSETS / "raw"
FONT_BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
FONT_MEDIUM = "/usr/share/fonts/opentype/noto/NotoSansCJK-Medium.ttc"
KR = 1  # NotoSansCJK .ttc 안의 한국어(KR) 글꼴 순서

NAVY = (0x0B, 0x15, 0x30)
NAVY_RAISED = (0x12, 0x1E, 0x3D)
NAVY_HIGH = (0x24, 0x36, 0x5F)
WHITE = (0xF4, 0xF6, 0xFC)
SUBTLE = (0xB8, 0xC4, 0xE6)
CYAN = (0x4D, 0xD8, 0xFF)

# 기기 캡처(1080×2340)에서 앱 화면만 남길 범위: 상태 바 아래 ~ 3버튼 내비게이션 바 위
APP_TOP, APP_BOTTOM = 130, 2190

SCREENSHOTS = [
    ("home", "누르는 동안만 움직이는 리모컨", "머리·발·전체 조정, 즐겨 쓰는 자세 4개"),
    ("routines", "삼성 모드 및 루틴으로 자동으로", "아침 기상 자세, 잘 때는 평평하게"),
    ("settings", "침대 여러 대도 한 앱에서", "계정·서버 없이 휴대폰과 침대만으로"),
]


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size, index=KR)


def gradient(size: tuple[int, int]) -> Image.Image:
    """위는 조금 밝고 아래로 갈수록 기본 네이비."""
    w, h = size
    top, bottom = NAVY_RAISED, NAVY
    col = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(h - 1, 1)
        col.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return col.resize((w, h))


def bed_art(width: int) -> Image.Image:
    art = render_splash.render(with_logos=False)
    art = art.crop(art.getchannel("A").getbbox())
    return art.resize((width, round(art.height * width / art.width)), Image.LANCZOS)


def feature_graphic() -> Image.Image:
    w, h = 1024, 500
    image = gradient((w, h)).convert("RGBA")
    draw = ImageDraw.Draw(image)
    x = 72
    draw.text((x, 128), "침대리모컨", font=font(FONT_BOLD, 76), fill=WHITE)
    draw.text((x, 240), "LINAK · 일룸 전동 침대를 휴대폰으로", font=font(FONT_MEDIUM, 30), fill=SUBTLE)
    draw.text((x, 300), "모드 및 루틴으로 아침 기상 자세까지", font=font(FONT_MEDIUM, 30), fill=CYAN)
    art = bed_art(400)
    image.alpha_composite(art, (w - art.width - 64, (h - art.height) // 2 + 10))
    return image.convert("RGB")


def screenshot(raw: Path, title: str, subtitle: str) -> Image.Image:
    w, h = 1080, 1920
    image = gradient((w, h)).convert("RGBA")
    draw = ImageDraw.Draw(image)
    title_font, sub_font = font(FONT_BOLD, 64), font(FONT_MEDIUM, 38)
    draw.text((w // 2, 150), title, font=title_font, fill=WHITE, anchor="ms")
    draw.text((w // 2, 230), subtitle, font=sub_font, fill=SUBTLE, anchor="ms")

    app = Image.open(raw).convert("RGB").crop((0, APP_TOP, 1080, APP_BOTTOM))
    shot_h = h - 300 - 60
    shot_w = round(app.width * shot_h / app.height)
    app = app.resize((shot_w, shot_h), Image.LANCZOS)
    left, top, radius, border = (w - shot_w) // 2, 300, 44, 4
    draw.rounded_rectangle(
        [left - border, top - border, left + shot_w + border, top + shot_h + border],
        radius=radius + border,
        fill=NAVY_HIGH,
    )
    mask = Image.new("L", (shot_w * 2, shot_h * 2), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, shot_w * 2 - 1, shot_h * 2 - 1], radius=radius * 2, fill=255)
    image.paste(app, (left, top), mask.resize((shot_w, shot_h), Image.LANCZOS))
    return image.convert("RGB")


if __name__ == "__main__":
    out = ASSETS / "feature-graphic-1024x500.png"
    feature_graphic().save(out)
    print(out)
    for i, (name, title, subtitle) in enumerate(SCREENSHOTS, start=1):
        out = ASSETS / f"screenshot-{i}-{name}.png"
        screenshot(RAW / f"{name}.png", title, subtitle).save(out)
        print(out)
