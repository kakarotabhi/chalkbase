import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IconName } from './icon-glyphs';
import { Icon } from './icon';

describe('Icon', () => {
  let fixture: ComponentFixture<Icon>;

  const svg = () => (fixture.nativeElement as HTMLElement).querySelector('svg');

  const render = (name: IconName, size?: number) => {
    fixture.componentRef.setInput('name', name);
    if (size !== undefined) {
      fixture.componentRef.setInput('size', size);
    }
    fixture.detectChanges();
  };

  beforeEach(() => {
    fixture = TestBed.createComponent(Icon);
  });

  it('draws the named glyph', () => {
    render('more');

    // Three dots, the design's overflow mark.
    expect(svg()?.querySelectorAll('circle')).toHaveLength(3);
  });

  it('draws glyphs made of several kinds of shape', () => {
    render('attendance');

    expect(svg()?.querySelectorAll('rect')).toHaveLength(1);
    expect(svg()?.querySelectorAll('path')).toHaveLength(3);
  });

  it('is decoration, so it is not announced', () => {
    render('school');

    // The label beside it carries the name; announcing both would say everything twice.
    expect(svg()?.getAttribute('aria-hidden')).toBe('true');
    expect(svg()?.getAttribute('focusable')).toBe('false');
  });

  it('takes its colour from the text around it', () => {
    render('school');

    expect(svg()?.getAttribute('stroke')).toBe('currentColor');
    expect(svg()?.getAttribute('fill')).toBe('none');
  });

  it('sizes to the caller, defaulting to the 20px the designs use', () => {
    render('school');
    expect(svg()?.getAttribute('width')).toBe('20');

    render('school', 28);
    expect(svg()?.getAttribute('width')).toBe('28');
    expect(svg()?.getAttribute('height')).toBe('28');
  });

  it('draws a plain square rather than nothing when the name is not one we have', () => {
    // Only reachable if a registry entry names an icon that was never drawn. A menu that loses an
    // item over a typo would be a worse failure than one square.
    render('not-drawn' as IconName);

    expect(svg()?.querySelectorAll('rect')).toHaveLength(1);
  });
});
