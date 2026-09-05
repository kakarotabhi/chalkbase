import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session-store';
import { MainLayout } from './main-layout';

describe('MainLayout', () => {
  let fixture: ComponentFixture<MainLayout>;

  const header = () => (fixture.nativeElement as HTMLElement).querySelector('.shell__header');

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MainLayout],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MainLayout);
  });

  it('names the school whose data is on screen', () => {
    TestBed.inject(SessionStore).signedIn(
      {
        userId: '2f1c9b60-1b1e-4a2f-9a1e-6c1f0f2b4d55',
        displayName: 'Priya Sharma',
        mustChangePassword: false,
        school: { code: 'GPS-S12', name: 'Greenfield Public School' },
        permissions: [],
      },
      'secret-one',
    );
    fixture.detectChanges();

    expect(header()?.textContent).toContain('Greenfield Public School');
    // And who is looking at it, from the same store.
    expect(header()?.querySelector('.user-menu__avatar')?.textContent).toBe('PS');
  });

  it('falls back to the product name rather than an empty header', () => {
    fixture.detectChanges();

    expect(header()?.textContent).toContain('Chalkbase');
  });
});
