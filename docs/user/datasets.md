# Datasets

A **dataset** is a list of climbs you keep to pick from: your home board's problems, a spray wall,
a gym circuit or a crag's lines. Find them under **You → Datasets**, under Places. Picking a climb
from a dataset when you log comes in a later update; for now you build and keep the lists.

<img src="../../app/src/test/screenshots/dataset_dark.png" width="300" alt="A dataset: search, sort chips and a list of climbs with their grades">

A dataset climb is not a climb in your journal: it's an entry in a list. Nothing in the Journal,
Progress or Places changes when you add, change or delete one.

## Making a dataset

Tap **+ → New dataset** (or **New dataset** on the empty page):

- **Name**, such as "Garage board" or "Block Lab circuit".
- **Board** (optional): one of the boards a place can have (Kilter, MoonBoard), **Custom board**
  for a spray wall or home board, or **No board** for a gym or crag list. A Kilter board asks for
  its size.
- **Boulder grades** and **Route grades**: the scales its climbs are graded in, or My settings.

**⋯ → Edit details** changes them later.

## Climbs in a dataset

**Add climb** opens a sheet: the name, Boulder or Route, the grade strip (or **No grade**), the
setter and a note. A name already in the dataset shows "Already in this dataset" but is allowed,
since gyms reuse names. Tap a climb to change it or delete it; a climb without a note goes at once
with **Undo**, and one with a note asks first.

Search finds a word in the name, setter or note. Sort by **Grade** (easiest first; climbs without
a grade come last, shown as "–"), **Name** or **Newest**.

**⋯ → Delete dataset** asks first. Its climbs go with it; climbs you logged are never touched.

## Import and export

**+ → Import a file** (or **⋯ → Import climbs** in a dataset) reads:

- **CSV**: UTF-8, comma or semicolon separated, with a header row. The columns are `name`,
  `grade`, `type` (boulder or route; boulder if left out), `setter` and `note`, in any order and
  any case. Only `name` is needed; rows without one are left out and counted, and other columns
  are listed and ignored.
- **Crux dataset file** (`.crux-dataset.json`), as Crux exports it, with the board and scales.

Before anything is saved, a preview shows how many climbs the file has, which grades aren't
recognised, where they go (a new dataset, or one you have) and the first rows. Grades are read in
the dataset's scales; change the scale there to read them again. A grade that isn't one of the
scale's keeps the climb without a grade and adds "Grade in file: …" to its note, so nothing is
lost. When climbs go into a dataset that already has one of the same name and type, pick **Skip**,
**Replace** or **Keep both**.

**⋯ → Export** saves the dataset as a CSV (the same five columns) or a Crux dataset file, through
Android's save dialog. Crux needs no storage permission for either.

Datasets are in your backups (the **Datasets** switch, on by default).
