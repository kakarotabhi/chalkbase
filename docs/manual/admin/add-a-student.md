# Add a student

Admitting a child: their record, who to contact about them, and which class they sit in.

**Who can do this:** the principal, the vice principal and the admission counsellor. Teachers and
the accountant can see students but not change them.

## Steps

1. Open **Students** from the menu, then **Add a student**.
2. Fill in the admission number, the child's name, date of birth and gender.
3. Save. You now have a record, but the child is not yet in a class.
4. Select **Add an enrolment**, choose the academic session, the class and the section. The roll
   number is optional — add it later when the class list settles.
5. Add the guardians (below).

## The name is one field

Type the name exactly as it appears on the documents the school will be held to — the birth
certificate, the transfer certificate, the board records. Do not split it, do not reorder it, and do
not invent a surname for a child who does not have one.

This is why there is no separate "surname" box. Many children have no surname, many have a single
name, and an initial is often a village or a father's name rather than a family name. A box that
demanded one would end up printed on a certificate.

## Guardians: search before you add

**Always search for the guardian before creating a new one.** A parent with three children at this
school should be **one** record linked to all three, not three copies.

This matters more than it looks. When one record is shared, correcting a father's phone number once
corrects it everywhere. With three copies, you fix one, the other two keep the old number, and
nothing tells you they disagree — you find out when nobody answers.

1. On the student's record, select **Add a guardian**.
2. Search by name or phone number. If the parent is already at the school — an older sibling, a
   previous admission — they will appear. Select them.
3. Only if the search genuinely finds nobody, create a new guardian.
4. Set the relation (father, mother, guardian, local guardian) and mark one guardian **primary** —
   that is who the school rings first. Only one guardian per child can be primary; marking a new one
   takes it off the previous.

To remove a guardian attached to the wrong child, use **Remove from this student**. The parent's own
record is not deleted — they may still be linked to other children.

## When a student leaves

Change their **status** — Transferred, Withdrawn, or Graduated. Do not look for a delete button;
there isn't one, deliberately.

A student's record is referenced by their fees, their attendance and their marks, and the school is
required to be able to produce those years later. Deleting the student would leave all of it
pointing at nothing. Changing the status takes them out of the lists people work from while keeping
the record whole.

## Contact, previous school and compliance

Below the guardians you will find four more sections on the record:

- **Contact** — the child's own address, phone and email, if different from a guardian's.
- **Previous school** — where they came from and their transfer certificate, if they have one. A
  fresh admission with no previous school simply has nothing here.
- **Medical** — blood group, CWSN/disability status, allergies, chronic conditions, medication, and
  an emergency contact.
- **Compliance & identifiers** — PEN/UDISE id, board registration number, caste and community,
  religion, EWS/BPL/RTE category, and an APAAR id.

All four are edited the same way as the details above: select **Edit**, fill in what you know, and
save. Nothing on a form is required unless the school has it.

## Documents

Below Compliance is **Documents**: the birth certificate, the transfer certificate, a report card,
the child's photo, their signature, or anything else the school keeps a scan of.

**Who can do this:** seeing a student's documents needs "View documents"; uploading, correcting or
deleting one needs "Manage documents". These are separate from every other permission on this
record, so a school can hand out either without the other — an office clerk who scans certificates
does not need to be able to edit a student's date of birth to do it, and someone who can already
manage the rest of the record does not automatically get this too.

1. Select **Add a document**, choose the type from the list, and pick the file. A PDF, a JPEG or a
   PNG, up to 8 MB.
2. Issue date and expiry date are both optional — a photo or a birth certificate has neither, and a
   transfer certificate or a certificate with a validity period usually has both.
3. Select **Upload**. Large files show progress while they travel; a file over the limit is refused
   immediately, with nothing uploaded.

Once uploaded, a document can be corrected (its type, its dates) or marked **Verified** or
**Rejected** once the office has checked it against the original — select **Edit** on that
document's card. **Delete** removes it outright; unlike the student record itself, there is no
reason to keep a wrongly attached file, so this is a real, permanent removal, not a status change.

**Downloading is always through this app, never a direct link.** Every download is checked against
your permission at the moment you ask for it and is written to the audit log as an export, the same
as any other document leaving the school's system.

**Caste certificates, Aadhaar copies and disability certificates do not belong here yet.** The kind
of document itself would disclose the same sensitive category a `caste` or `disability` field would,
and that needs the same protection those fields are still waiting on. Attaching one under **Other**
is not a safe substitute — ask before you do, and expect this to be revisited once that protection
exists.

## Masked fields, and who can see them

A caste, a religion, a category, a disability status, an allergy, a medication or an APAAR id is
shown as **"Recorded, masked"**, not the value, even to someone who can edit the record. Seeing the
real value needs a separate permission — **"Reveal restricted student data"** — and select
**Reveal**. Every reveal is written to the audit log: this is the most sensitive data the school
holds, and looking at it is treated as a deliberate act, not something that happens as a side effect
of opening a student's page.

**Who can do this:** by default, only the principal. A school that wants another role to hold it —
an office administrator who prepares the UDISE+ return, say — adds "Reveal restricted student data"
to that role.

## APAAR needs consent recorded

An APAAR id cannot be saved unless **Consent given** is ticked and the name of who gave consent is
recorded — the person who signed the consent form, which is not always the same guardian as the
primary contact. This is not a formality: without it, the record has no basis for holding the id at
all, and the save is refused.

## What is not here yet

Anything to do with transport or hostel is **not yet available** on this record — those will arrive
as a need flag at most, once those modules exist, since this record was never meant to hold a route
or a room assignment of its own.

## What is recorded

Every change here goes to the audit log: who added or edited a student, who attached a guardian,
who changed an enrolment, who uploaded, corrected or deleted a document, and when — and, separately,
who revealed a masked caste, religion, category, medical or APAAR field, and when, and who
downloaded a document. The log records which fields changed and never the values, and it never
contains a child's name or admission number.
