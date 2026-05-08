import { TestBed } from '@angular/core/testing';

import { DxfConverterService } from './dxf-converter';

describe('DxfConverterService', () => {
  let service: DxfConverterService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DxfConverterService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
