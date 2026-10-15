"""Builds app/src/main/assets/board_holes.json from a Boardsesh hardware catalogue.

Crux draws board climbs from hold positions only: for each board it knows, where every hold a
climb can name sits, and (for Kilter) the edges of each size and the LED that lights each hold on
it. No climbs, names or images.

Usage: python -I scripts/board_holes.py <catalog.db> [out.json]

The catalogue is listed in https://snapshots.boardsesh.com/board-snapshots/v1-catalog/manifest.json.
"""
import json
import sqlite3
import sys

# Boardsesh (board type, layout id) for each Crux BoardModel, and the product its sizes belong to.
LAYOUTS = {
    "kilter:1": ("kilter", 1, 1),
    "kilter:8": ("kilter", 8, 7),
    "moonboard:2": ("moonboard", 2, 1),
    "moonboard:3": ("moonboard", 3, 1),
    "moonboard:4": ("moonboard", 4, 1),
    "moonboard:5": ("moonboard", 5, 1),
    "moonboard:6": ("moonboard", 6, 1),
    "moonboard:7": ("moonboard", 7, 1),
}


def build(path):
    db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    out = {}
    for key, (board, layout, product) in LAYOUTS.items():
        rows = db.execute(
            "SELECT p.id, h.id, h.x, h.y, h.name FROM board_placements p JOIN board_holes h "
            "ON h.id = p.hole_id AND h.board_type = p.board_type WHERE p.board_type = ? AND p.layout_id = ? ORDER BY p.id",
            (board, layout),
        ).fetchall()
        if board == "kilter":
            # A Kilter climb names its holds by placement.
            points = {str(pid): [x, y] for pid, _, x, y, _ in rows}
            sizes = {
                str(sid): [left, right, bottom, top]
                for sid, left, right, bottom, top in db.execute(
                    "SELECT id, edge_left, edge_right, edge_bottom, edge_top FROM board_product_sizes WHERE board_type = ? AND product_id = ?",
                    (board, product),
                )
            }
            # Each size numbers its LEDs its own way: by placement, the LED that lights it.
            leds = {}
            for sid in sizes:
                rows_ = db.execute(
                    "SELECT p.id, l.position FROM board_placements p JOIN board_leds l "
                    "ON l.hole_id = p.hole_id AND l.board_type = p.board_type WHERE p.board_type = ? AND p.layout_id = ? AND l.product_size_id = ?",
                    (board, layout, int(sid)),
                ).fetchall()
                if rows_:
                    leds[sid] = {str(pid): pos for pid, pos in rows_}
            out[key] = {"points": points, "sizes": sizes, "leds": leds}
        else:
            # A MoonBoard climb names its holds by hole: a grid cell like F4.
            points = {str(hid): [x, y, name] for _, hid, x, y, name in rows}
            out[key] = {"points": points, "rows": max(y for _, _, _, y, _ in rows), "columns": max(x for _, _, x, _, _ in rows)}
    return out


if __name__ == "__main__":
    data = build(sys.argv[1])
    target = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/assets/board_holes.json"
    with open(target, "w", encoding="utf-8") as f:
        json.dump(data, f, separators=(",", ":"), sort_keys=True)
    print(target, {k: len(v["points"]) for k, v in data.items()})
