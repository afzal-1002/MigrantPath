import { AnswerValue } from '../../core/services/assessment.service';
import { isBlank, toAnswerRequest, valueFromAnswer } from './answer-mapping';

function answer(overrides: Partial<AnswerValue>): AnswerValue {
  return {
    stringValue: null,
    booleanValue: null,
    integerValue: null,
    decimalValue: null,
    dateValue: null,
    referenceCode: null,
    selectedOptionCodes: null,
    unsure: false,
    ...overrides,
  };
}

describe('answer-mapping', () => {
  describe('valueFromAnswer / toAnswerRequest round-trip', () => {
    it('BOOLEAN', () => {
      const form = valueFromAnswer('BOOLEAN', answer({ booleanValue: true }));
      expect(form).toEqual({ value: true, unsure: false });
      expect(toAnswerRequest('BOOLEAN', form)).toEqual({ booleanValue: true });
    });

    it('MULTI_SELECT', () => {
      const form = valueFromAnswer('MULTI_SELECT', answer({ selectedOptionCodes: ['WORK', 'STUDY'] }));
      expect(form).toEqual({ value: ['WORK', 'STUDY'], unsure: false });
      expect(toAnswerRequest('MULTI_SELECT', form)).toEqual({ selectedOptionCodes: ['WORK', 'STUDY'] });
    });

    it('MULTI_SELECT defaults to an empty array when unanswered', () => {
      expect(valueFromAnswer('MULTI_SELECT', null)).toEqual({ value: [], unsure: false });
    });

    it('DECIMAL', () => {
      const form = valueFromAnswer('DECIMAL', answer({ decimalValue: 15000.5 }));
      expect(toAnswerRequest('DECIMAL', form)).toEqual({ decimalValue: 15000.5 });
    });

    it('COUNTRY reuses referenceCode', () => {
      const form = valueFromAnswer('COUNTRY', answer({ referenceCode: 'PK' }));
      expect(form).toEqual({ value: 'PK', unsure: false });
      expect(toAnswerRequest('COUNTRY', form)).toEqual({ referenceCode: 'PK' });
    });

    it('SINGLE_SELECT also reuses referenceCode', () => {
      const form = valueFromAnswer('SINGLE_SELECT', answer({ referenceCode: 'MARRIED' }));
      expect(toAnswerRequest('SINGLE_SELECT', form)).toEqual({ referenceCode: 'MARRIED' });
    });

    it('an unsure answer maps to a blank value and the unsure flag', () => {
      const form = valueFromAnswer('DECIMAL', answer({ unsure: true }));
      expect(form.unsure).toBe(true);
      expect(toAnswerRequest('DECIMAL', form)).toEqual({ unsure: true });
    });

    it('no existing answer maps to a blank, non-unsure form value', () => {
      expect(valueFromAnswer('TEXT', null)).toEqual({ value: '', unsure: false });
    });
  });

  describe('isBlank', () => {
    it('a marked-unsure value is never blank', () => {
      expect(isBlank('DECIMAL', { value: null, unsure: true })).toBe(false);
    });

    it('an empty MULTI_SELECT array is blank', () => {
      expect(isBlank('MULTI_SELECT', { value: [], unsure: false })).toBe(true);
      expect(isBlank('MULTI_SELECT', { value: ['WORK'], unsure: false })).toBe(false);
    });

    it('a null BOOLEAN is blank; true/false are not', () => {
      expect(isBlank('BOOLEAN', { value: null, unsure: false })).toBe(true);
      expect(isBlank('BOOLEAN', { value: false, unsure: false })).toBe(false);
    });

    it('an empty string is blank', () => {
      expect(isBlank('TEXT', { value: '', unsure: false })).toBe(true);
      expect(isBlank('TEXT', { value: 'Warsaw', unsure: false })).toBe(false);
    });
  });
});
