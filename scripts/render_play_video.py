#!/usr/bin/env python3
"""Play 스토어용 시연 영상(1080×1920, H.264)을 만든다.

store/raw/demo.mp4(화면 녹화 1080×2400)에서 상태 바·제스처 바를 잘라 내고, 스크린샷과 같은
틀(그라데이션 바탕 + 둥근 앱 화면 + 위쪽 설명 문구)에 얹는다. 설명 문구는 구간마다 바뀐다.
원본 녹화는 debug 빌드의 가상 침대로 에뮬레이터에서 찍는다 (실제 침대 구동 없음):
머리 ▲ 4초 → 발 ▲ 3초 → 프리셋 3 → 프리셋 1.

필요: pillow, imageio-ffmpeg (ffmpeg 실행 파일). 글꼴·색은 render_play_assets.py와 같다.
사용: python3 scripts/render_play_video.py  (출력: store/demo-bed-moving.mp4)
"""
import subprocess
import sys
import tempfile
from pathlib import Path

import imageio_ffmpeg
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from render_play_assets import (  # noqa: E402
    ASSETS, FONT_BOLD, FONT_MEDIUM, NAVY_HIGH, RAW, SUBTLE, WHITE, font, gradient,
)

W, H, FPS = 1080, 1920, 30
# 녹화(1080×2400)에서 앱 화면만 남길 범위: 상태 바 아래 ~ 제스처 바 위 (높이는 짝수)
APP_TOP, APP_HEIGHT = 94, 2236
SHOT_TOP, SHOT_H, RADIUS, BORDER = 300, 1560, 44, 4
SHOT_W = round(1080 * SHOT_H / APP_HEIGHT) // 2 * 2
LEFT = (W - SHOT_W) // 2
END_HOLD_S = 1.5

# (시작 초, 끝 초, 제목, 부제) — 녹화 타이밍(프리셋 3을 누르는 시점 ≈ 12초)에 맞춘다
CAPTIONS = [
    (0, 12, "누르는 동안만 움직이는 리모컨", "머리·발을 원하는 만큼 올리고 내리기"),
    (12, 999, "즐겨 쓰는 자세로 한 번에", "프리셋을 누르면 그 자세까지 알아서"),
]


def background() -> Image.Image:
    image = gradient((W, H)).convert("RGBA")
    ImageDraw.Draw(image).rounded_rectangle(
        [LEFT - BORDER, SHOT_TOP - BORDER, LEFT + SHOT_W + BORDER, SHOT_TOP + SHOT_H + BORDER],
        radius=RADIUS + BORDER,
        fill=NAVY_HIGH,
    )
    return image.convert("RGB")


def caption(title: str, subtitle: str) -> Image.Image:
    image = Image.new("RGBA", (W, SHOT_TOP - 20), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.text((W // 2, 150), title, font=font(FONT_BOLD, 64), fill=WHITE, anchor="ms")
    draw.text((W // 2, 230), subtitle, font=font(FONT_MEDIUM, 38), fill=SUBTLE, anchor="ms")
    return image


def mask() -> Image.Image:
    big = Image.new("L", (SHOT_W * 2, SHOT_H * 2), 0)
    ImageDraw.Draw(big).rounded_rectangle([0, 0, SHOT_W * 2 - 1, SHOT_H * 2 - 1], radius=RADIUS * 2, fill=255)
    return big.resize((SHOT_W, SHOT_H), Image.LANCZOS)


def render(raw: Path, out: Path) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        background().save(tmp / "bg.png")
        mask().save(tmp / "mask.png")
        inputs = ["-loop", "1", "-i", str(tmp / "bg.png"), "-i", str(raw), "-loop", "1", "-i", str(tmp / "mask.png")]
        graph = [
            f"[1:v]fps={FPS},crop=1080:{APP_HEIGHT}:0:{APP_TOP},scale={SHOT_W}:{SHOT_H}:flags=lanczos,"
            f"tpad=stop_mode=clone:stop_duration={END_HOLD_S},format=rgba[app]",
            "[2:v]format=gray[m]",
            "[app][m]alphamerge[shot]",
            f"[0:v][shot]overlay={LEFT}:{SHOT_TOP}:shortest=1[v0]",
        ]
        for i, (start, end, title, subtitle) in enumerate(CAPTIONS):
            caption(title, subtitle).save(tmp / f"cap{i}.png")
            inputs += ["-loop", "1", "-i", str(tmp / f"cap{i}.png")]
            graph.append(f"[v{i}][{3 + i}:v]overlay=0:0:shortest=1:enable='between(t,{start},{end})'[v{i + 1}]")
        # 바탕·마스크·문구는 무한 반복 입력이라 길이를 녹화 길이 + 끝 정지로 못 박는다
        _, raw_s = imageio_ffmpeg.count_frames_and_secs(str(raw))
        subprocess.run(
            [imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-loglevel", "error", "-y", *inputs,
             "-filter_complex", ";".join(graph), "-map", f"[v{len(CAPTIONS)}]", "-an",
             "-c:v", "libx264", "-profile:v", "high", "-crf", "20", "-pix_fmt", "yuv420p",
             "-r", str(FPS), "-t", f"{raw_s + END_HOLD_S:.2f}", "-movflags", "+faststart", str(out)],
            check=True,
        )


if __name__ == "__main__":
    out = ASSETS / "demo-bed-moving.mp4"
    render(RAW / "demo.mp4", out)
    print(out)
