import _ from "underscore";
import { updateIn } from "icepick";

import { normalizeParameterValue } from "metabase/parameters/utils/parameter-values";
import {
  getParametersMappedToCard,
  remapParameterValuesForTemplateTags,
} from "metabase/parameters/utils/cards";

import * as Q_DEPRECATED from "metabase/lib/query"; // legacy
import Utils from "metabase/lib/utils";

import type { StructuredQuery } from "metabase-types/types/Query";
import type {
  Card,
  DatasetQuery,
  StructuredDatasetQuery,
  NativeDatasetQuery,
} from "metabase-types/types/Card";
import type {
  Parameter,
  ParameterMapping,
  ParameterValues,
} from "metabase-types/types/Parameter";
import type Metadata from "metabase-lib/lib/metadata/Metadata";
import type Table from "metabase-lib/lib/metadata/Table";
import Question from "../../metabase-lib/lib/Question";

// TODO Atte Keinänen 6/5/17 Should these be moved to corresponding metabase-lib classes?
// Is there any reason behind keeping them in a central place?

export const STRUCTURED_QUERY_TEMPLATE: StructuredDatasetQuery = {
  type: "query",
  database: null,
  query: {
    "source-table": null,
    aggregation: undefined,
    breakout: undefined,
    filter: undefined,
  },
};

export const NATIVE_QUERY_TEMPLATE: NativeDatasetQuery = {
  type: "native",
  database: null,
  native: {
    query: "",
    "template-tags": {},
  },
};

export function isStructured(card: Card): boolean {
  return card.dataset_query.type === "query";
}

export function isNative(card: Card): boolean {
  return card.dataset_query.type === "native";
}

export function cardVisualizationIsEquivalent(
  cardA: Card,
  cardB: Card,
): boolean {
  return _.isEqual(
    _.pick(cardA, "display", "visualization_settings"),
    _.pick(cardB, "display", "visualization_settings"),
  );
}

export function cardQueryIsEquivalent(cardA: Card, cardB: Card): boolean {
  cardA = updateIn(cardA, ["dataset_query", "parameters"], p => p || []);
  cardB = updateIn(cardB, ["dataset_query", "parameters"], p => p || []);
  return _.isEqual(
    _.pick(cardA, "dataset_query"),
    _.pick(cardB, "dataset_query"),
  );
}

export function cardIsEquivalent(
  cardA: Card,
  cardB: Card,
  { checkParameters = false }: { checkParameters: boolean } = {},
): boolean {
  return (
    cardQueryIsEquivalent(cardA, cardB) &&
    cardVisualizationIsEquivalent(cardA, cardB) &&
    (!checkParameters ||
      _.isEqual(cardA.parameters || [], cardB.parameters || []))
  );
}

export function getQuery(card: Card): ?StructuredQuery {
  if (card.dataset_query.type === "query") {
    return card.dataset_query.query;
  } else {
    return null;
  }
}

export function getTableMetadata(card: Card, metadata: Metadata): ?Table {
  const query = getQuery(card);
  if (query && query["source-table"] != null) {
    return metadata.table(query["source-table"]) || null;
  }
  return null;
}

// NOTE Atte Keinänen 7/5/17: Still used in dashboards and public questions.
// Query builder uses `Question.getResults` which contains similar logic.
export function applyParameters(
  card: Card,
  parameters: Parameter[],
  parameterValues: ParameterValues = {},
  parameterMappings: ParameterMapping[] = [],
): DatasetQuery {
  const datasetQuery = Utils.copy(card.dataset_query);
  // clean the query
  if (datasetQuery.type === "query") {
    datasetQuery.query = Q_DEPRECATED.cleanQuery(datasetQuery.query);
  }
  datasetQuery.parameters = [];
  for (const parameter of parameters || []) {
    const value = parameterValues[parameter.id];
    if (value == null) {
      continue;
    }

    const cardId = card.id || card.original_card_id;
    const mapping = _.findWhere(
      parameterMappings,
      cardId != null
        ? {
            card_id: cardId,
            parameter_id: parameter.id,
          }
        : // NOTE: this supports transient dashboards where cards don't have ids
          // BUT will not work correctly with multiseries dashcards since
          // there's no way to identify which card the mapping applies to.
          {
            parameter_id: parameter.id,
          },
    );

    const type = parameter.type;
    if (mapping) {
      // mapped target, e.x. on a dashboard
      datasetQuery.parameters.push({
        type,
        value: normalizeParameterValue(type, value),
        target: mapping.target,
        id: parameter.id,
      });
    } else if (parameter.target) {
      // inline target, e.x. on a card
      datasetQuery.parameters.push({
        type,
        value: normalizeParameterValue(type, value),
        target: parameter.target,
        id: parameter.id,
      });
    }
  }

  return datasetQuery;
}

export function isTransientId(id: ?any) {
  return id != null && typeof id === "string" && isNaN(parseInt(id));
}

export function questionUrlWithParameters(
  card,
  metadata,
  parameters,
  parameterValues,
  parameterMappings,
) {
  const dashboardParametersMappedToCard = getParametersMappedToCard(
    card,
    parameters,
    parameterMappings,
  );

  const question = new Question(card, metadata);
  if (question.isStructured()) {
    return question
      .setParameters(dashboardParametersMappedToCard)
      .setParameterValues(parameterValues)
      .getUrlWithParameters();
  } else if (question.isNative()) {
    const templateTagParameters = question.parameters();
    const parameterValuesByTemplateTag = remapParameterValuesForTemplateTags(
      dashboardParametersMappedToCard,
      templateTagParameters,
      parameterValues,
    );

    return question
      .setParameterValues(parameterValuesByTemplateTag)
      .setParameterValues(parameterValuesByTemplateTag)
      .getUrlWithParameters();
  }
}
