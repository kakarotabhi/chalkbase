import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NavigationStore } from '../../core/navigation/navigation-store';
import { NoDestination } from './no-destination';

describe('NoDestination', () => {
  let fixture: ComponentFixture<NoDestination>;
  let navigation: NavigationStore;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [NoDestination] }).compileComponents();

    navigation = TestBed.inject(NavigationStore);
    fixture = TestBed.createComponent(NoDestination);
  });

  it('tells someone with no screens what to do about it', () => {
    // The menu arrived and had nothing this build can open in it. That is an account or a release
    // problem, and the office is who fixes it.
    navigation.load([]);
    fixture.detectChanges();

    expect(text()).toContain('no screens are available to you');
    expect(text()).toContain('school office');
  });

  it('says so differently when the menu never arrived', () => {
    // Nothing loaded: `/api/me` failed. Telling this person to ask about their role would send
    // them to the office over a dropped connection.
    fixture.detectChanges();

    expect(text()).toContain('could not be loaded');
    expect(text()).toContain('reload');
  });

  it('never shows an empty screen — whichever state it is in, it says something', () => {
    fixture.detectChanges();
    expect(text().trim().length).toBeGreaterThan(0);

    navigation.load([]);
    fixture.detectChanges();
    expect(text().trim().length).toBeGreaterThan(0);
  });
});
