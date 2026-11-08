package club.midlife.rb1speed;

/** Visual themes only: all scales and readings remain GPS speed in mph. */
public enum DashboardSkin {
    RB1("rb1", "Honda Odyssey Absolute RB1", "ODYSSEY", "RB1", 0xff02040a, 0xff020611, 0xff72efff, 0xff20c9ff, 0xffff4015, 0xff0047ff, "sans-serif-light", 140, 260, 20),
    R32("r32", "Nissan Skyline GT-R R32", "SKYLINE GT-R", "R32", 0xff080909, 0xff141616, 0xfff1eee4, 0xffd5d6ca, 0xffff652f, 0xffa3b5a3, "sans-serif-condensed", 135, 270, 20),
    SUPRA("a80", "Toyota Supra A80", "SUPRA", "A80", 0xff100b06, 0xff1c130a, 0xffffc178, 0xffeaa552, 0xffff602d, 0xffb46a20, "sans-serif-condensed", 130, 280, 20),
    RX7("fd3s", "Mazda RX-7 FD3S", "RX-7", "FD3S", 0xff090909, 0xff151313, 0xfff4ead4, 0xffcfc5ac, 0xffef362c, 0xff9e372e, "sans-serif", 145, 250, 10),
    INTEGRA("dc2", "Honda Integra Type R DC2", "INTEGRA TYPE R", "DC2", 0xff101112, 0xfff0eee4, 0xff20242a, 0xff252930, 0xffdb252b, 0xffead450, "sans-serif-condensed", 135, 270, 20);

    public final String id, label, title, code, font;
    public final int background, face, ink, tick, pointer, accent, labelStep;
    public final float start, sweep;
    DashboardSkin(String id, String label, String title, String code, int background, int face,
            int ink, int tick, int pointer, int accent, String font, float start, float sweep, int labelStep) {
        this.id=id; this.label=label; this.title=title; this.code=code; this.background=background;
        this.face=face; this.ink=ink; this.tick=tick; this.pointer=pointer; this.accent=accent;
        this.font=font; this.start=start; this.sweep=sweep; this.labelStep=labelStep;
    }
    public static DashboardSkin fromId(String id) {
        for (DashboardSkin skin : values()) if (skin.id.equals(id)) return skin;
        return RB1;
    }
}
