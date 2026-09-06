# Import students from a spreadsheet

Load your existing student roll into Chalkbase in one go, instead of typing it in.

**Who can do this:** the principal, the vice principal and the admission counsellor.

## Before you start

Set up your **academic session** and your **classes and sections** first. The spreadsheet names its
classes — `Class 5`, `A` — and Chalkbase has to be able to match those against classes you have
already created. If you import before setting them up, you will be told so rather than left guessing.

## Steps

1. Open **Students → Import from a spreadsheet**.
2. **Download the template.** It has the right column names in the right form, which removes the
   commonest problem before it happens.
3. Fill it in from your existing sheet, then save it as **CSV** (in Excel: File → Save As → CSV).
4. Choose the academic year you are importing into, and the file.
5. Select **Check the file**. Nothing is saved at this point — this only looks.
6. Read what comes back. If there are problems, each one names the row and the column and says what
   is wrong. Fix them in your spreadsheet, save, and check again.
7. When there are no problems, select **Import these students**.

## Why it checks first, and why it is all or nothing

If even one row has a problem, **nothing** is imported.

That sounds strict and it is the kinder behaviour. The alternative — importing the good rows and
telling you about the rest — leaves you with a half-loaded school and no easy way to finish: fixing
the remaining rows and uploading again would either duplicate the ones that worked or fail on every
one of them.

All or nothing means your file is either loaded or it is not. Fix a typo, upload again, and nothing
can go wrong twice.

## The columns

`admission_number`, `full_name`, `date_of_birth`, `gender`, `status`, `admitted_on`, `class`,
`section`, `roll_number`.

`status`, `admitted_on` and `roll_number` may be left empty. The order of the columns does not
matter, and capitals do not matter.

- **Dates** are `yyyy-MM-dd` — 14 June 2015 is `2015-06-14`.
- **Gender** is `MALE`, `FEMALE` or `OTHER`. A single `M`, `F` or `O` is accepted too, since that is
  what most school sheets hold.
- **`full_name`** is the whole name in one column, exactly as it appears on the documents the school
  will be held to. Do not split it and do not invent a surname.
- **A name containing a comma** must be in quotes: `"Nair, Meera"`. Excel does this for you when it
  saves as CSV.
- **`class` and `section`** must match classes you have already set up. If they do not, the message
  lists the names your school actually has.

## Common problems

**"This school has no classes set up yet."** Go to Academics and create your classes and sections
first.

**"That looks like an Excel file."** Save it as CSV: File → Save As → CSV.

**A column is not recognised.** The message names it. Compare against the template — `dob` is not
`date_of_birth`.

**Two rows with the same admission number.** The message names the other row so you can see which
two clash.

**The list of problems says "showing the first 200".** There are more. Fix these, check again, and
the rest will be listed.

## What Chalkbase does with the file

It reads it and throws it away. The file is not stored, not kept, and never attached to any record.
The problems it reports name the row and the column and **never quote what was in the cell**, so the
list is safe to share with a colleague or print out while you work through it.

Guardians are not imported yet — add them from each student's record, searching for a parent who is
already here before creating a new one.

## What is recorded

One entry in the audit log: who imported, into which academic year, and how many students. Not six
hundred separate entries, and no child's name or date of birth.
