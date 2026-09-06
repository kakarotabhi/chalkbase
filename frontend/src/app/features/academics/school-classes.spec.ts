import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SchoolClass } from '../../core/api/models';
import { SchoolClasses } from './school-classes';

const CLASSES_URL = '/api/academics/classes';
const ORDER_URL = '/api/academics/classes/order';
const SECTIONS_URL = '/api/academics/sections';

/** An invented ladder for an invented school. Never real school data in a fixture. */
const NURSERY: SchoolClass = {
  id: 'cls-nursery',
  name: 'Nursery',
  sequence: 10,
  active: true,
  sections: [
    { id: 'sec-n-a', name: 'A', active: true },
    { id: 'sec-n-b', name: 'B', active: false },
  ],
};

const CLASS_ONE: SchoolClass = {
  id: 'cls-one',
  name: 'Class 1',
  sequence: 20,
  active: true,
  sections: [{ id: 'sec-1-a', name: 'A', active: true }],
};

/** A rung the school stopped running. It stays in the ladder — see the component. */
const CLASS_TWO: SchoolClass = {
  id: 'cls-two',
  name: 'Class 2',
  sequence: 30,
  active: false,
  sections: [],
};

const LADDER = [NURSERY, CLASS_ONE, CLASS_TWO];

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-06T10:00:00Z',
  traceId: 'test-trace',
  data,
});

const refusal = (code: string, details?: Record<string, string>) => ({
  success: false,
  timestamp: '2026-09-06T10:00:00Z',
  error: { code, message: 'Refused.', details },
});

describe('SchoolClasses', () => {
  let fixture: ComponentFixture<SchoolClasses>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const ladderRequest = () => httpMock.expectOne({ url: CLASSES_URL, method: 'GET' });

  const control = (id: string) => element().querySelector<HTMLButtonElement>(`#${id}`);
  const press = (id: string) => {
    control(id)!.click();
    fixture.detectChanges();
  };

  const rungNames = () =>
    Array.from(element().querySelectorAll('.rung__name')).map((node) => node.textContent?.trim());

  const type = (value: string) => {
    const input = element().querySelector('#class-name') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const saveEditor = () => {
    const save = Array.from(element().querySelectorAll('cb-button button')).find(
      (candidate) => (candidate.textContent ?? '').trim() === 'Save',
    ) as HTMLButtonElement;
    save.click();
    fixture.detectChanges();
  };

  const arrive = (ladder: readonly SchoolClass[] = LADDER) => {
    fixture = TestBed.createComponent(SchoolClasses);
    fixture.detectChanges();
    ladderRequest().flush(envelope(ladder));
    fixture.detectChanges();
  };

  /** Answers the quiet refetch every successful write makes. */
  const settleRefresh = (ladder: readonly SchoolClass[]) => {
    ladderRequest().flush(envelope(ladder));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SchoolClasses],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // ── The ladder ───────────────────────────────────────────────────────────────────────────

  it('shows the ladder in the order the server gave it, with each class and its sections', () => {
    arrive();

    expect(rungNames()).toEqual(['Nursery', 'Class 1', 'Class 2']);
    expect(control('class-add-section-cls-nursery')).not.toBeNull();
    expect(text()).toContain('1 of 2 sections running');
    expect(text()).toContain('None yet');
  });

  /**
   * Inactive rows stay in the ladder rather than hiding behind a filter, and that is not only a
   * presentation choice: `PUT /classes/order` takes every id, so the order the user sees has to be
   * the order that gets sent. A filtered client would have the server renumber what was left and
   * the school would lose a rung.
   */
  it('keeps a class that stopped running in its place, flagged rather than hidden', () => {
    arrive();

    const inactive = element().querySelectorAll('.rung--inactive');
    expect(inactive).toHaveLength(1);
    expect(inactive[0].textContent).toContain('Class 2');
    expect(inactive[0].textContent).toContain('Not running');
    // And it is still part of the ladder it can be moved within.
    expect(control('class-up-cls-two')).not.toBeNull();
  });

  it('flags a section that stopped running without hiding it either', () => {
    arrive();

    const inactive = element().querySelector('.section--inactive');
    expect(inactive?.textContent).toContain('B');
    expect(inactive?.textContent).toContain('Not running');
    expect(control('section-active-sec-n-b')?.textContent).toContain('Start running');
  });

  /**
   * ADR-0019: rows are deactivated, never removed, because by the time anything references a
   * section it is too late to decide that deleting it was wrong. So there is no delete anywhere on
   * this screen, and nothing that reads like one.
   */
  it('offers no way to delete anything, deliberately', () => {
    arrive();

    expect(text()).not.toContain('Delete');
    expect(text()).not.toContain('Remove');
    expect(text()).toContain('Nothing here is ever deleted');
  });

  it('says a school with no classes has none, and does not guess a ladder for it', () => {
    arrive([]);

    expect(text()).toContain('No classes yet');
    // ADR-0019 is explicit that no standard ladder is seeded: schools genuinely disagree.
    expect(text()).toContain('does not assume one');
    expect(element().querySelector('.ladder')).toBeNull();
  });

  it('offers a retry when the ladder cannot be loaded', () => {
    fixture = TestBed.createComponent(SchoolClasses);
    fixture.detectChanges();
    ladderRequest().flush(refusal('GEN_001'), { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not load classes and sections');

    (
      Array.from(element().querySelectorAll('button')).find((candidate) =>
        (candidate.textContent ?? '').includes('Try again'),
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();
    ladderRequest().flush(envelope(LADDER));
    fixture.detectChanges();

    expect(rungNames()).toEqual(['Nursery', 'Class 1', 'Class 2']);
  });

  it('explains a refusal calmly instead of crashing or redirecting', () => {
    fixture = TestBed.createComponent(SchoolClasses);
    fixture.detectChanges();
    ladderRequest().flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view classes and sections');
    expect(control('class-add')).toBeNull();
    expect(text()).not.toContain('Try again');
  });

  // ── Classes ──────────────────────────────────────────────────────────────────────────────

  it('adds a class, and says where the new one landed', () => {
    arrive([NURSERY]);

    press('class-add');
    type('Class 1');
    saveEditor();

    const created = httpMock.expectOne({ url: CLASSES_URL, method: 'POST' });
    expect(created.request.body).toEqual({ name: 'Class 1' });
    created.flush(envelope(CLASS_ONE));
    fixture.detectChanges();
    settleRefresh([NURSERY, CLASS_ONE]);

    // Create takes no position, so the screen has to say where it went and how to move it.
    expect(text()).toContain('Class 1 added at the end of the ladder');
    expect(rungNames()).toEqual(['Nursery', 'Class 1']);
  });

  /** Renaming must not switch a class off or on as a side effect of the PUT that carries `active`. */
  it('renames a class without changing whether it is running', () => {
    arrive();

    press('class-rename-cls-two');
    expect((element().querySelector('#class-name') as HTMLInputElement).value).toBe('Class 2');

    type('Class II');
    saveEditor();

    const saved = httpMock.expectOne({ url: `${CLASSES_URL}/${CLASS_TWO.id}`, method: 'PUT' });
    expect(saved.request.body).toEqual({ name: 'Class II', active: false });
    saved.flush(envelope({ ...CLASS_TWO, name: 'Class II' }));
    fixture.detectChanges();
    settleRefresh([NURSERY, CLASS_ONE, { ...CLASS_TWO, name: 'Class II' }]);

    expect(rungNames()).toEqual(['Nursery', 'Class 1', 'Class II']);
  });

  /**
   * Deactivating is one labelled button on the row, not a checkbox inside an edit form. ADR-0019
   * makes this the normal way a class stops running, and it has to feel like one.
   */
  it('stops and starts a class running, in one press each way', () => {
    arrive();

    press('class-active-cls-one');
    const stopped = httpMock.expectOne({ url: `${CLASSES_URL}/${CLASS_ONE.id}`, method: 'PUT' });
    expect(stopped.request.body).toEqual({ name: 'Class 1', active: false });
    stopped.flush(envelope({ ...CLASS_ONE, active: false }));
    fixture.detectChanges();
    settleRefresh([NURSERY, { ...CLASS_ONE, active: false }, CLASS_TWO]);

    expect(text()).toContain('Class 1 is no longer running');
    expect(text()).toContain('can be brought back');

    press('class-active-cls-one');
    const started = httpMock.expectOne({ url: `${CLASSES_URL}/${CLASS_ONE.id}`, method: 'PUT' });
    expect(started.request.body).toEqual({ name: 'Class 1', active: true });
    started.flush(envelope(CLASS_ONE));
    fixture.detectChanges();
    settleRefresh(LADDER);

    expect(text()).toContain('Class 1 is running again.');
  });

  // ── Sections ─────────────────────────────────────────────────────────────────────────────

  it('adds a section to the class it was asked for', () => {
    arrive();

    press('class-add-section-cls-one');
    type('B');
    saveEditor();

    const created = httpMock.expectOne({
      url: `${CLASSES_URL}/${CLASS_ONE.id}/sections`,
      method: 'POST',
    });
    expect(created.request.body).toEqual({ name: 'B' });
    created.flush(envelope({ id: 'sec-1-b', name: 'B', active: true }));
    fixture.detectChanges();
    settleRefresh([
      NURSERY,
      {
        ...CLASS_ONE,
        sections: [...CLASS_ONE.sections, { id: 'sec-1-b', name: 'B', active: true }],
      },
      CLASS_TWO,
    ]);

    expect(text()).toContain('Section B added.');
    expect(control('section-rename-sec-1-b')).not.toBeNull();
  });

  /**
   * A section is addressed on its own — `PUT /academics/sections/{id}` — and its `active` goes back
   * unchanged, for the same reason a class rename carries its own.
   */
  it('renames a section, and re-reads the ladder because renaming can reorder it', () => {
    arrive();

    press('section-rename-sec-n-b');
    type('C');
    saveEditor();

    const saved = httpMock.expectOne({ url: `${SECTIONS_URL}/sec-n-b`, method: 'PUT' });
    expect(saved.request.body).toEqual({ name: 'C', active: false });
    saved.flush(envelope({ id: 'sec-n-b', name: 'C', active: false }));
    fixture.detectChanges();

    // Sections come back ordered by name, so a rename can move one. Patching the row in place
    // would show an order the server does not have.
    settleRefresh(LADDER);
    expect(text()).toContain('Section renamed to C.');
  });

  it('stops and starts a section running', () => {
    arrive();

    press('section-active-sec-1-a');
    const stopped = httpMock.expectOne({ url: `${SECTIONS_URL}/sec-1-a`, method: 'PUT' });
    expect(stopped.request.body).toEqual({ name: 'A', active: false });
    stopped.flush(envelope({ id: 'sec-1-a', name: 'A', active: false }));
    fixture.detectChanges();
    settleRefresh(LADDER);

    expect(text()).toContain('A of Class 1 is no longer running');
  });

  it('shows a refused name under the field that was refused', () => {
    arrive();

    press('class-add');
    type('Nursery');
    saveEditor();

    httpMock
      .expectOne({ url: CLASSES_URL, method: 'POST' })
      .flush(refusal('VAL_001', { name: 'A class called Nursery already exists.' }), {
        status: 400,
        statusText: 'Bad Request',
      });
    fixture.detectChanges();

    expect(text()).toContain('A class called Nursery already exists.');
    expect((element().querySelector('#class-name') as HTMLInputElement).value).toBe('Nursery');
  });

  /**
   * The uniqueness constraint does not account for `active`, so a class that stopped running still
   * holds its name. "Class 2 already exists" is baffling unless the screen points at the
   * switched-off row holding it — which is also the reason an inactive class is never hidden here.
   */
  it('points a duplicate name at the switched-off row that is holding it', () => {
    arrive();

    press('class-add');
    type('Class 2');
    saveEditor();

    httpMock
      .expectOne({ url: CLASSES_URL, method: 'POST' })
      .flush(refusal('ACAD_004'), { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text()).toContain('Class 2 is already in the ladder, switched off');
    expect(text()).toContain('position 3');
    expect(text()).toContain('Start it running again rather than adding a second one');
    // And the row it points at is on screen, with the control that fixes it.
    expect(control('class-active-cls-two')?.textContent).toContain('Start running');
  });

  it('explains a duplicate name held by a class that is still running', () => {
    arrive();

    press('class-add');
    type('Nursery');
    saveEditor();

    httpMock
      .expectOne({ url: CLASSES_URL, method: 'POST' })
      .flush(refusal('ACAD_004'), { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text()).toContain('Nursery is already in the ladder');
    expect(text()).toContain('two rungs cannot share a name');
  });

  // ── Reordering ───────────────────────────────────────────────────────────────────────────

  /**
   * The one thing on this screen that must not be wrong. The server refuses a partial list, and
   * for a good reason: a client that sent only the rows it happened to be showing would have the
   * ladder renumbered around the ones it left out.
   */
  it('sends every class id, in the new order, including the ones that stopped running', () => {
    arrive();

    press('class-down-cls-nursery');

    const reordered = httpMock.expectOne({ url: ORDER_URL, method: 'PUT' });
    expect(reordered.request.body).toEqual({
      classIds: [CLASS_ONE.id, NURSERY.id, CLASS_TWO.id],
    });
    // Class 2 is not running and is in the list anyway.
    expect(reordered.request.body.classIds).toContain(CLASS_TWO.id);

    reordered.flush(envelope([CLASS_ONE, NURSERY, CLASS_TWO]));
    fixture.detectChanges();

    expect(rungNames()).toEqual(['Class 1', 'Nursery', 'Class 2']);
  });

  it('moves the row straight away and then renders what the server says, not its own guess', () => {
    arrive();

    press('class-up-cls-two');

    // Optimistic: the row has already moved, before the request has been answered.
    expect(rungNames()).toEqual(['Nursery', 'Class 2', 'Class 1']);
    expect(text()).toContain('Class 2 moved up to position 2 of 3.');

    // The server is the authority on the order, so its answer is what gets rendered — even when it
    // is not the order that was asked for.
    httpMock
      .expectOne({ url: ORDER_URL, method: 'PUT' })
      .flush(envelope([CLASS_TWO, NURSERY, CLASS_ONE]));
    fixture.detectChanges();

    expect(rungNames()).toEqual(['Class 2', 'Nursery', 'Class 1']);
  });

  it('puts the ladder back when the reorder fails, and says that it did', () => {
    arrive();

    press('class-down-cls-nursery');
    expect(rungNames()).toEqual(['Class 1', 'Nursery', 'Class 2']);

    httpMock
      .expectOne({ url: ORDER_URL, method: 'PUT' })
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(rungNames()).toEqual(['Nursery', 'Class 1', 'Class 2']);
    expect(text()).toContain('Nursery could not be moved. The ladder is as it was.');
    expect(text()).toContain('You do not have permission to change classes and sections');
  });

  /**
   * Disabled at the ends rather than removed: a button that disappears at the top of the list and
   * reappears further down is a target that moves under the finger reaching for it.
   */
  it('will not move the first class up or the last class down', () => {
    arrive();

    expect(control('class-up-cls-nursery')!.disabled).toBe(true);
    expect(control('class-down-cls-nursery')!.disabled).toBe(false);
    expect(control('class-down-cls-two')!.disabled).toBe(true);

    control('class-up-cls-nursery')!.click();
    fixture.detectChanges();
    httpMock.expectNone({ url: ORDER_URL, method: 'PUT' });
  });

  /**
   * Keyboard support is the whole reason this is buttons rather than a drag surface, and it is only
   * real if focus survives the move. The row has just travelled up the document; leaving focus on
   * the body would drop a keyboard user back at the start of the page after every press.
   *
   * When the pressed button is the one that has just become disabled — the class reached an end of
   * the ladder — focus goes to its opposite rather than nowhere.
   */
  it('keeps focus on the arrows as the row moves under them', async () => {
    arrive();
    document.body.appendChild(fixture.nativeElement);

    control('class-down-cls-nursery')!.focus();
    press('class-down-cls-nursery');
    httpMock
      .expectOne({ url: ORDER_URL, method: 'PUT' })
      .flush(envelope([CLASS_ONE, NURSERY, CLASS_TWO]));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(document.activeElement).toBe(control('class-down-cls-nursery'));

    press('class-down-cls-nursery');
    httpMock
      .expectOne({ url: ORDER_URL, method: 'PUT' })
      .flush(envelope([CLASS_ONE, CLASS_TWO, NURSERY]));
    fixture.detectChanges();
    await fixture.whenStable();

    // Nursery is now last, so "move down" is disabled and focus lands on "move up" instead.
    expect(control('class-down-cls-nursery')!.disabled).toBe(true);
    expect(document.activeElement).toBe(control('class-up-cls-nursery'));

    fixture.nativeElement.remove();
  });

  /** Every move button names the class it moves, so it works read out of context. */
  it('names what each move button moves', () => {
    arrive();

    expect(control('class-up-cls-one')!.textContent).toContain('Move Class 1 up');
    expect(control('class-down-cls-one')!.textContent).toContain('Move Class 1 down');
  });

  /**
   * Two moves in quick succession. Each request carries a complete order, so the later one is the
   * user's intent; an earlier answer that arrives after it must not repaint an order nobody asked
   * for any more.
   */
  it('ignores an answer to a move that a later move has already overtaken', () => {
    arrive();

    press('class-down-cls-nursery');
    const first = httpMock.expectOne({ url: ORDER_URL, method: 'PUT' });

    press('class-down-cls-nursery');
    const second = httpMock.expectOne({ url: ORDER_URL, method: 'PUT' });
    expect(second.request.body).toEqual({
      classIds: [CLASS_ONE.id, CLASS_TWO.id, NURSERY.id],
    });

    // The first answer lands last, describing an order that has been superseded.
    second.flush(envelope([CLASS_ONE, CLASS_TWO, NURSERY]));
    fixture.detectChanges();
    first.flush(envelope([CLASS_ONE, NURSERY, CLASS_TWO]));
    fixture.detectChanges();

    expect(rungNames()).toEqual(['Class 1', 'Class 2', 'Nursery']);
  });
});
