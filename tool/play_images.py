#!/usr/bin/env python3
"""Draws the Google Play listing images (icon 512, feature graphic 1024x500)
into fastlane/metadata/android/<locale>/images, from the launcher icon.

    python3 tool/play_images.py

Needs Pillow (with raqm, for Hebrew) and the Inter and DejaVu fonts.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FOREGROUND = ROOT / 'android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png'
START, END = (0x63, 0x66, 0xF1), (0x28, 0xA3, 0xB9)
INTER = '/usr/share/fonts/opentype/inter/Inter-%s.otf'
HEBREW = '/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf'

LOCALES = {
    'en-US': dict(tagline='Photos without the trail.', pill='End-to-end encrypted', rtl=False),
    'iw-IL': dict(tagline='תמונות בלי עקבות.', pill='מוצפן מקצה לקצה', rtl=True),
}


def gradient(w, h):
    """The brand gradient, top left to bottom right."""
    img = Image.new('RGB', (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / w + y / h) / 2
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(START, END))
    return img


def glyph(size):
    fg = Image.open(FOREGROUND).convert('RGBA')
    # The adaptive icon's safe zone is the middle two thirds.
    m = fg.width // 6
    return fg.crop((m, m, fg.width - m, fg.height - m)).resize((size, size), Image.LANCZOS)


def icon():
    img = gradient(512, 512).convert('RGBA')
    g = glyph(384)
    img.alpha_composite(g, (64, 64))
    return img


def feature(tagline, pill, rtl):
    w, h = 1024, 500
    img = gradient(w, h).convert('RGBA')
    over = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    for box in ((-200, -300, 400, 330), (-100, 330, 300, 760)):
        d.ellipse(box, outline=(255, 255, 255, 50), width=2)
    img.alpha_composite(over)

    # The app tile, with a soft shadow.
    tile = (720, 140, 940, 360)
    shadow = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (tile[0], tile[1] + 16, tile[2], tile[3] + 16), 56, fill=(20, 30, 90, 90))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(24)))
    over = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(over).rounded_rectangle(
        tile, 56, fill=(255, 255, 255, 46), outline=(255, 255, 255, 90), width=2)
    img.alpha_composite(over)
    img.alpha_composite(glyph(200), (730, 150))

    d = ImageDraw.Draw(img)
    right = 664
    d.text((right, 132), 'ZERO-TRUST', font=ImageFont.truetype(INTER % 'Medium', 26),
           fill=(255, 255, 255, 220), anchor='rm')
    d.text((right, 196), 'Camera', font=ImageFont.truetype(INTER % 'Bold', 84),
           fill='white', anchor='rm')
    if rtl:
        tag_font = ImageFont.truetype(HEBREW % '', 34)
        pill_font = ImageFont.truetype(HEBREW % '-Bold', 18)
        kw = dict(direction='rtl', language='he')
    else:
        tag_font = ImageFont.truetype(INTER % 'Regular', 36)
        pill_font = ImageFont.truetype(INTER % 'SemiBold', 18)
        kw = {}
    d.text((right, 280), tagline, font=tag_font, fill=(255, 255, 255, 240), anchor='rm', **kw)

    # The pill: a small lock and the promise.
    tw = d.textlength(pill, font=pill_font, **kw)
    pw = tw + 62
    box = (right - pw, 337, right, 385)
    over = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(over).rounded_rectangle(box, 24, fill=(20, 30, 90, 90))
    img.alpha_composite(over)
    d = ImageDraw.Draw(img)
    lock_x = box[2] - 34 if rtl else box[0] + 20
    text_x = box[0] + 20 if rtl else box[0] + 44
    d.rounded_rectangle((lock_x, 358, lock_x + 14, 370), 3, fill='white')
    d.arc((lock_x + 2, 349, lock_x + 12, 365), 180, 360, fill='white', width=2)
    d.text((text_x, 361), pill, font=pill_font, fill='white', anchor='lm', **kw)
    return img.convert('RGB')


for locale, text in LOCALES.items():
    out = ROOT / 'fastlane/metadata/android' / locale / 'images'
    out.mkdir(parents=True, exist_ok=True)
    icon().save(out / 'icon.png')
    feature(**text).save(out / 'featureGraphic.png')
    print('wrote', out)
