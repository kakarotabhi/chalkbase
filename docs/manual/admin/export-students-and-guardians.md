# Export students and guardians

Download the roster, or the guardian directory, as a spreadsheet file you can open, filter and
share.

**Who can do this:** anyone who can see the students list can export it. The unmasked export is
separate and is not held by any role Chalkbase ships with — see below.

## Steps

1. Open **Students** or **Students → Guardians**.
2. Select **Export**.
3. The file downloads as a CSV — a plain spreadsheet file every version of Excel, Google Sheets and
   LibreOffice can open.

Whatever your search box and filters currently show is what gets exported. Filter to one class or
section first if that is all you need.

## What is in the file, and what is not

The export includes the same information as the student record: admission number, name, date of
birth, class and section, contact details, guardians, previous school, and — where it has been
recorded — an emergency contact.

**It does not include a child's caste, religion, category, disability status, allergies, chronic
conditions, medication or blood group.** Those columns are simply not in the file. This is not a
setting you can turn off from this screen: it is what "masked" means for these fields everywhere in
Chalkbase, and the export follows the same rule as the student record's own screen.

## The unmasked export

A second button, **Export unmasked**, appears only for someone whose role holds "Export unmasked
student data." No role Chalkbase ships with holds it — a principal who needs it (most commonly for
a UDISE+ return) adds the permission to a role from **Settings → Roles and access**.

Selecting it asks you to confirm first, because this is not a quiet action: the file will include
every child's caste, religion, category, disability status, allergies, chronic conditions,
medication and blood group, for every student your current filters match, and it is recorded in the
audit log — which fields were disclosed and how many students, not the values themselves — every
time it is used.

Use it when you actually need those fields in one file, not as a habit. The ordinary export above
is safe to run whenever you like.

## Common questions

**Why does the file have fewer columns than I expected?** You are looking at the masked export.
Compare the column count against a file downloaded with **Export unmasked**, if your role holds it.

**Can I export just the students in one class?** Yes — set the class and section filter on the
students list first, then export. The file only contains what the filtered list shows.

**Is the guardian export the same, masked and unmasked?** There is no unmasked guardian export,
because nothing in the guardian directory is a field Chalkbase treats as Restricted — a phone
number, a name and an occupation are already in the ordinary file.

## What is recorded

Every export — masked or unmasked — writes one entry in the audit log: who exported, from which
screen, which columns were in the file, and how many rows. Never a child's name, never a value from
any column. The masked export's entry never lists a Restricted column, because it never held one;
the unmasked export's entry does, which is what makes it traceable afterwards.
