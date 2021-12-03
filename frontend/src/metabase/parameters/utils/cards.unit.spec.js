import MetabaseSettings from "metabase/lib/settings";
import {
  getTemplateTagParameters,
  remapParameterValuesToTemplateTags,
} from "./cards";

MetabaseSettings.get = jest.fn();

function mockFieldFilterOperatorsFlag(value) {
  MetabaseSettings.get.mockImplementation(flag => {
    if (flag === "field-filter-operators-enabled?") {
      return value;
    }
  });
}

describe("parameters/utils/cards", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    MetabaseSettings.get.mockReturnValue(false);
  });

  describe("getTemplateTagParameters", () => {
    let tags;
    beforeEach(() => {
      tags = [
        {
          "widget-type": "foo",
          type: "string",
          id: 1,
          name: "a",
          "display-name": "A",
          default: "abc",
        },
        {
          type: "string",
          id: 2,
          name: "b",
          "display-name": "B",
        },
        {
          type: "number",
          id: 3,
          name: "c",
          "display-name": "C",
        },
        {
          type: "date",
          id: 4,
          name: "d",
          "display-name": "D",
        },
        {
          "widget-type": "foo",
          type: "dimension",
          id: 5,
          name: "e",
          "display-name": "E",
        },
        {
          type: null,
          id: 6,
        },
        {
          type: "dimension",
          id: 7,
          name: "f",
          "display-name": "F",
        },
      ];
    });

    describe("field filter operators enabled", () => {
      beforeEach(() => {
        mockFieldFilterOperatorsFlag(true);
      });

      it("should convert tags into tag parameters with field filter operator types", () => {
        const parametersWithFieldFilterOperatorTypes = [
          {
            default: "abc",
            id: 1,
            name: "A",
            slug: "a",
            target: ["variable", ["template-tag", "a"]],
            type: "foo",
          },
          {
            default: undefined,
            id: 2,
            name: "B",
            slug: "b",
            target: ["variable", ["template-tag", "b"]],
            type: "string/=",
          },
          {
            default: undefined,
            id: 3,
            name: "C",
            slug: "c",
            target: ["variable", ["template-tag", "c"]],
            type: "number/=",
          },
          {
            default: undefined,
            id: 4,
            name: "D",
            slug: "d",
            target: ["variable", ["template-tag", "d"]],
            type: "date/single",
          },
          {
            default: undefined,
            id: 5,
            name: "E",
            slug: "e",
            target: ["dimension", ["template-tag", "e"]],
            type: "foo",
          },
        ];

        expect(getTemplateTagParameters(tags)).toEqual(
          parametersWithFieldFilterOperatorTypes,
        );
      });
    });

    describe("field filter operators disabled", () => {
      it("should convert tags into tag parameters", () => {
        const parameters = [
          {
            default: "abc",
            id: 1,
            name: "A",
            slug: "a",
            target: ["variable", ["template-tag", "a"]],
            type: "foo",
          },
          {
            default: undefined,
            id: 2,
            name: "B",
            slug: "b",
            target: ["variable", ["template-tag", "b"]],
            type: "category",
          },
          {
            default: undefined,
            id: 3,
            name: "C",
            slug: "c",
            target: ["variable", ["template-tag", "c"]],
            type: "category",
          },
          {
            default: undefined,
            id: 4,
            name: "D",
            slug: "d",
            target: ["variable", ["template-tag", "d"]],
            type: "date/single",
          },
          {
            default: undefined,
            id: 5,
            name: "E",
            slug: "e",
            target: ["dimension", ["template-tag", "e"]],
            type: "foo",
          },
        ];
        expect(getTemplateTagParameters(tags)).toEqual(parameters);
      });
    });
  });

  describe("remapParameterValuesToTemplateTags", () => {
    it("should convert a dashboard parameterValues map into a map of template tag values", () => {
      const parameterValues = {
        "dashboard-parameter-1": "aaa",
        "dashboard-parameter-2": "bbb",
        "dashboard-parameter-3": "ccc",
      };

      const dashboardParameters = [
        {
          id: "dashboard-parameter-1",
          target: ["variable", ["template-tag", "template-tag-1"]],
        },
        {
          id: "dashboard-parameter-2",
          target: ["dimension", ["template-tag", "template-tag-2"]],
        },
        {
          id: "dashboard-parameter-3",
          target: ["dimension", ["field", 1, null]],
        },
        {
          id: "dashboard-parameter-4",
        },
      ];

      const templateTags = [
        {
          name: "template-tag-1",
        },
        {
          name: "template-tag-2",
        },
        {
          name: "template-tag-3",
        },
      ];

      expect(
        remapParameterValuesToTemplateTags(
          templateTags,
          dashboardParameters,
          parameterValues,
        ),
      ).toEqual({
        "template-tag-1": "aaa",
        "template-tag-2": "bbb",
      });
    });
  });
});
