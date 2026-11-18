import sys, os
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools import subset

src, out = sys.argv[1], sys.argv[2]
FR = os.path.join(src, "ofl_fraunces_Fraunces[SOFT,WONK,opsz,wght].ttf")
PJ = os.path.join(src, "ofl_plusjakartasans_PlusJakartaSans[wght].ttf")
jobs = [
    (FR, "fraunces_regular.ttf", {"opsz": 48, "wght": 400, "SOFT": 50, "WONK": 0}, "Regular", 400),
    (FR, "fraunces_semibold.ttf", {"opsz": 72, "wght": 600, "SOFT": 50, "WONK": 0}, "SemiBold", 600),
    (PJ, "plusjakartasans_medium.ttf", {"wght": 500}, "Medium", 500),
    (PJ, "plusjakartasans_semibold.ttf", {"wght": 600}, "SemiBold", 600),
    (PJ, "plusjakartasans_bold.ttf", {"wght": 700}, "Bold", 700),
    (PJ, "plusjakartasans_extrabold.ttf", {"wght": 800}, "ExtraBold", 800),
]
unicodes = list(range(0x20, 0x7F)) + list(range(0xA0, 0x250)) + [
    0x0259, 0x2026, 0x2212, 0x2192, 0x00B7, 0x2013, 0x2014, 0x00AB, 0x00BB]
REQUIRED = "ƏəĞğIıİiÖöÜüÇçŞş"
for path, name, axes, style, weight in jobs:
    f = TTFont(path)
    axis_tags = {a.axisTag for a in f["fvar"].axes}
    loc = {k: v for k, v in axes.items() if k in axis_tags}
    assert set(loc) == axis_tags, (name, axis_tags)
    inst = instancer.instantiateVariableFont(f, loc, updateFontNames=False)
    opts = subset.Options()
    opts.layout_features = subset.Options().layout_features + ["tnum", "case", "locl"]
    opts.name_IDs = ["*"]
    opts.notdef_outline = True
    opts.glyph_names = False
    opts.hinting = False
    sub = subset.Subsetter(opts)
    sub.populate(unicodes=unicodes)
    sub.subset(inst)
    # OS/2 weight class must match the static instance
    inst["OS/2"].usWeightClass = weight
    dst = os.path.join(out, name)
    inst.save(dst)
    chk = TTFont(dst)
    cmap = chk.getBestCmap()
    missing = [c for c in REQUIRED + "−·…" if ord(c) not in cmap]
    feats = sorted({fr.FeatureTag for fr in chk["GSUB"].table.FeatureList.FeatureRecord}) if "GSUB" in chk else []
    print(f"{name}: {os.path.getsize(dst)//1024} KB, glyphs={len(chk.getGlyphOrder())}, missing={missing}, fvar={'fvar' in chk}, GSUB={feats}")
