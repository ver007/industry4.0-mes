/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo Framework
 * Version: 1.4
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.productFlowThruDivision.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.qcadoo.mes.materialFlow.MaterialFlowService;
import org.apache.commons.collections.MultiHashMap;
import org.apache.commons.collections.MultiMap;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.qcadoo.localization.api.TranslationService;
import com.qcadoo.mes.basic.constants.BasicConstants;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.basic.constants.UnitConversionItemFieldsB;
import com.qcadoo.mes.basicProductionCounting.constants.BasicProductionCountingConstants;
import com.qcadoo.mes.basicProductionCounting.constants.ProductionCountingQuantityFields;
import com.qcadoo.mes.basicProductionCounting.constants.ProductionCountingQuantityRole;
import com.qcadoo.mes.basicProductionCounting.constants.ProductionCountingQuantityTypeOfMaterial;
import com.qcadoo.mes.materialFlow.constants.LocationFields;
import com.qcadoo.mes.materialFlow.constants.MaterialFlowConstants;
import com.qcadoo.mes.materialFlowResources.MaterialFlowResourcesService;
import com.qcadoo.mes.materialFlowResources.service.DocumentManagementService;
import com.qcadoo.mes.orders.constants.OrderFields;
import com.qcadoo.mes.orders.constants.OrdersConstants;
import com.qcadoo.mes.productFlowThruDivision.constants.ProductFlowThruDivisionConstants;
import com.qcadoo.mes.productFlowThruDivision.constants.ProductionCountingQuantityFieldsPFTD;
import com.qcadoo.mes.productFlowThruDivision.constants.ProductsToIssue;
import com.qcadoo.mes.productFlowThruDivision.constants.Range;
import com.qcadoo.mes.productFlowThruDivision.constants.TechnologyFieldsPFTD;
import com.qcadoo.mes.productFlowThruDivision.hooks.TechnologyHooksPFTD;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.CreationDocumentResponse;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.UpdateIssuesLocationsQuantityStatusHolder;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.WarehouseIssueParameterService;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.constans.CollectionProducts;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.constans.IssueFields;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.constans.ProductsToIssueFields;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.constans.WarehouseIssueFields;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.states.constants.WarehouseIssueState;
import com.qcadoo.mes.productFlowThruDivision.warehouseIssue.states.constants.WarehouseIssueStateChangeFields;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.mes.technologies.states.constants.TechnologyState;
import com.qcadoo.model.api.BigDecimalUtils;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.model.api.exception.RuntimeExceptionWithArguments;
import com.qcadoo.model.api.search.SearchCriteriaBuilder;
import com.qcadoo.model.api.search.SearchProjections;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.model.api.units.PossibleUnitConversions;
import com.qcadoo.model.api.units.UnitConversionService;
import com.qcadoo.security.api.SecurityService;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;

@Service
public class WarehouseIssueService {

    public static final String L_LOCATION = "location";

    public static final String L_PRODUCT = "product";

    @Autowired
    private DocumentManagementService documentManagementService;

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private NumberService numberService;

    @Autowired
    private MaterialFlowResourcesService materialFlowResourcesService;

    @Autowired
    private TechnologyHooksPFTD technologyHooksPFTD;

    @Autowired
    private UnitConversionService unitConversionService;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private WarehouseIssueParameterService warehouseIssueParameterService;

    @Autowired
    private WarehouseIssueDocumentsService warehouseIssueDocumentsService;

    public List<Entity> fillProductsToIssue(Long warehouseIssueId, CollectionProducts collectionProducts, ProductsToIssue productsToIssue, Entity toc,
            Entity divisionEntity) {
        if (warehouseIssueId == null) {
            return null;
        }

        Entity warehouseIssue = getwarehouseIssueDD().get(warehouseIssueId);
        if (warehouseIssueParameterService.issueForOrder()) {
            warehouseIssue.setField("productsToIssues", Lists.newArrayList());
            warehouseIssue.getDataDefinition().save(warehouseIssue);
            Entity order = dataDefinitionService.get(OrdersConstants.PLUGIN_IDENTIFIER, OrdersConstants.MODEL_ORDER).get(
                    warehouseIssue.getBelongsToField(WarehouseIssueFields.ORDER).getId());

            if (collectionProducts == CollectionProducts.ON_ORDER) {
                if (productsToIssue != null && productsToIssue == ProductsToIssue.ONLY_MATERIALS) {
                    return createProductIssueEntryForOrder(warehouseIssue, order);
                } else if (productsToIssue != null && productsToIssue == ProductsToIssue.ALL_INPUT_PRODUCTS) {
                    return createProductIssueEntryForOrder(warehouseIssue, order);
                } else if (productsToIssue != null && productsToIssue == ProductsToIssue.FINAL_PRODUCTS) {
                    return createProductIssueEntryForOrderFinalProduct(warehouseIssue, order);
                }
            }else if (collectionProducts == CollectionProducts.ON_DIVISION) {
                return createProductIssueEntryForDivision(divisionEntity, warehouseIssue, order);
            } else if (collectionProducts == CollectionProducts.ON_OPERATION) {
                return createProductIssueEntryForOperation(toc, warehouseIssue, order);
            }
        }
        return null;
    }

    private List<Entity> createProductIssueEntryForOperation(final Entity toc, final Entity warehouseIssue, final Entity order) {
        if (toc != null) {
            List<Entity> coverageProducts = getProductionCoutingQuantityDD()
                    .find()
                    .add(SearchRestrictions.belongsTo(ProductionCountingQuantityFields.ORDER, order))
                    .add(SearchRestrictions.eq(ProductionCountingQuantityFields.ROLE,
                            ProductionCountingQuantityRole.USED.getStringValue()))
                    .add(SearchRestrictions.eq(ProductionCountingQuantityFields.TYPE_OF_MATERIAL,
                            ProductionCountingQuantityTypeOfMaterial.COMPONENT.getStringValue()))
                    .add(SearchRestrictions.belongsTo(ProductionCountingQuantityFields.TECHNOLOGY_OPERATION_COMPONENT, toc))
                    .list().getEntities();
            coverageProducts = filterCoverageProducts(coverageProducts, warehouseIssue);
            Map<Long, BigDecimal> quantitiesForProducts = materialFlowResourcesService.getQuantitiesForProductsAndLocation(
                    coverageProducts.stream()
                            .map(coverageProduct -> coverageProduct.getBelongsToField(ProductionCountingQuantityFields.PRODUCT))
                            .distinct().collect(Collectors.toList()),
                    warehouseIssue.getBelongsToField(WarehouseIssueFields.PLACE_OF_ISSUE));

            return createProductForIssueEntry(coverageProducts, warehouseIssue, quantitiesForProducts);
        }
        return null;
    }

    private List<Entity> createProductIssueEntryForDivision(final Entity divisionEntity, final Entity warehouseIssue,
            final Entity order) {
        if (divisionEntity != null) {
            String range = order.getBelongsToField(OrderFields.TECHNOLOGY).getStringField(TechnologyFieldsPFTD.RANGE);
            List<Entity> coverageProducts = getProductionCoutingQuantityDD()
                    .find()
                    .add(SearchRestrictions.belongsTo(ProductionCountingQuantityFields.ORDER, order))
                    .add(SearchRestrictions.eq(ProductionCountingQuantityFields.ROLE,
                            ProductionCountingQuantityRole.USED.getStringValue()))
                    .add(SearchRestrictions.eq(ProductionCountingQuantityFields.TYPE_OF_MATERIAL,
                            ProductionCountingQuantityTypeOfMaterial.COMPONENT.getStringValue())).list().getEntities();
            coverageProducts = filterCoverageProducts(coverageProducts, warehouseIssue);
            Map<Long, BigDecimal> quantitiesForProducts = materialFlowResourcesService.getQuantitiesForProductsAndLocation(
                    coverageProducts.stream()
                            .map(coverageProduct -> coverageProduct.getBelongsToField(ProductionCountingQuantityFields.PRODUCT))
                            .distinct().collect(Collectors.toList()),
                    warehouseIssue.getBelongsToField(WarehouseIssueFields.PLACE_OF_ISSUE));

            if (range.equals(Range.ONE_DIVISION.getStringValue())) {
                return createProductForIssueEntry(coverageProducts, warehouseIssue, quantitiesForProducts);
            } else {
                coverageProducts = filterCoverageProductsToDivision(coverageProducts, divisionEntity);
                return createProductForIssueEntry(coverageProducts, warehouseIssue, quantitiesForProducts);
            }
        }
        return null;
    }

    private List<Entity> createProductIssueEntryForOrder(final Entity warehouseIssue, final Entity order) {
        List<Entity> coverageProducts = getProductionCoutingQuantityDD()
                .find()
                .add(SearchRestrictions.belongsTo(ProductionCountingQuantityFields.ORDER, order))
                .add(SearchRestrictions.eq(ProductionCountingQuantityFields.ROLE,
                        ProductionCountingQuantityRole.USED.getStringValue()))
                .add(SearchRestrictions.eq(ProductionCountingQuantityFields.TYPE_OF_MATERIAL,
                        ProductionCountingQuantityTypeOfMaterial.COMPONENT.getStringValue())).list().getEntities();
        coverageProducts = filterCoverageProducts(coverageProducts, warehouseIssue);
        Map<Long, BigDecimal> quantitiesForProducts = materialFlowResourcesService.getQuantitiesForProductsAndLocation(
                coverageProducts.stream()
                        .map(coverageProduct -> coverageProduct.getBelongsToField(ProductionCountingQuantityFields.PRODUCT))
                        .distinct().collect(Collectors.toList()),
                warehouseIssue.getBelongsToField(WarehouseIssueFields.PLACE_OF_ISSUE));
        return createProductForIssueEntry(coverageProducts, warehouseIssue, quantitiesForProducts);
    }

    private List<Entity> filterCoverageProducts(List<Entity> coverageProducts, Entity warehouseIssue) {
        List<Entity> filteredCoverageProducts = Lists.newArrayList();
        if (warehouseIssue.getStringField(WarehouseIssueFields.PRODUCTS_TO_ISSUE_MODE).equals(
                ProductsToIssue.ONLY_MATERIALS.getStrValue())) {
            for (Entity cProduct : coverageProducts) {
                SearchCriteriaBuilder scb = dataDefinitionService
                        .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY)
                        .find()
                        .setProjection(SearchProjections.id())
                        .add(SearchRestrictions.belongsTo(TechnologyFields.PRODUCT,
                                cProduct.getBelongsToField(ProductionCountingQuantityFields.PRODUCT)))
                        .add(SearchRestrictions.isNull(TechnologyFields.TECHNOLOGY_TYPE))
                        .add(SearchRestrictions.eq(TechnologyFields.STATE, TechnologyState.ACCEPTED.getStringValue()))
                        .add(SearchRestrictions.eq(TechnologyFields.MASTER, true));

                if (scb.setMaxResults(1).uniqueResult() == null) {
                    filteredCoverageProducts.add(cProduct);
                }
            }
            coverageProducts = filteredCoverageProducts;
        }
        return coverageProducts;
    }

    public List<Entity> createProductForIssueEntry(final List<Entity> coverageProducts, Entity warehouseIssue,
            final Map<Long, BigDecimal> quantities) {
        Multimap<Entity, Entity> groupedCoverageProducts = groupCoverageProductsByWarehouse(coverageProducts);
        List<Entity> createdProductsToIssue = Lists.newArrayList();
        for (Entity warehouse : groupedCoverageProducts.keySet()) {
            Collection<Entity> coverageProductsForWarehouse = groupedCoverageProducts.get(warehouse);

            Map<Long, BigDecimal> quantitiesForProductsInWarehouse = materialFlowResourcesService
                    .getQuantitiesForProductsAndLocation(
                            coverageProductsForWarehouse
                                    .stream()
                                    .map(coverageProduct -> coverageProduct
                                            .getBelongsToField(ProductionCountingQuantityFields.PRODUCT))
                                    .collect(Collectors.toList()), warehouse);
            for (Entity coverageProduct : coverageProductsForWarehouse) {
                Entity product = coverageProduct.getBelongsToField(ProductionCountingQuantityFields.PRODUCT);
                Entity componentsLocation = coverageProduct
                        .getBelongsToField(ProductionCountingQuantityFieldsPFTD.COMPONENTS_LOCATION);
                Entity productToIssue = findExistingProductToIssue(product, warehouseIssue, componentsLocation);
                if (productToIssue == null) {
                    productToIssue = getProductsToIssueDD().create();
                    productToIssue.setField(ProductsToIssueFields.ISSUED, false);
                    productToIssue.setField("warehouseIssue", warehouseIssue);
                    productToIssue.setField("product", product);
                    productToIssue.setField("demandQuantity",
                            coverageProduct.getDecimalField(ProductionCountingQuantityFields.PLANNED_QUANTITY));
                } else {

                    productToIssue.setField(
                            "demandQuantity",
                            coverageProduct.getDecimalField(ProductionCountingQuantityFields.PLANNED_QUANTITY).add(
                                    productToIssue.getDecimalField("demandQuantity")));
                }
                Entity location = warehouseIssue.getBelongsToField(WarehouseIssueFields.PLACE_OF_ISSUE);
                BigDecimal resourceQuantity = BigDecimal.ZERO;
                if (quantities == null || quantities.size() == 0) {
                    resourceQuantity = materialFlowResourcesService.getResourcesQuantityForLocationAndProduct(location, product);
                } else {
                    resourceQuantity = quantities.get(product.getId());
                }

                productToIssue.setField("locationsQuantity", resourceQuantity);

                Entity opic = dataDefinitionService
                        .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_OPERATION_PRODUCT_IN_COMPONENT)
                        .find()
                        .add(SearchRestrictions.belongsTo("operationComponent", coverageProduct
                                .getBelongsToField(ProductionCountingQuantityFields.TECHNOLOGY_OPERATION_COMPONENT)))
                        .add(SearchRestrictions.belongsTo("product", BasicConstants.PLUGIN_IDENTIFIER,
                                BasicConstants.MODEL_PRODUCT, product.getId())).setMaxResults(1).uniqueResult();
                productToIssue.setField("productInComponent", opic);
                productToIssue.setField(L_LOCATION, componentsLocation);

                productToIssue.setField("placeOfIssueQuantity", quantitiesForProductsInWarehouse.get(product.getId()));

                BigDecimal issueQuantity = BigDecimal.ZERO;

                issueQuantity = getIssuedQuantityForProductAndOrder(warehouseIssue, product);
                productToIssue.setField("issueQuantity", issueQuantity);
                reCalculateDemandQuantity(productToIssue);
                createdProductsToIssue.add(productToIssue.getDataDefinition().save(productToIssue));
            }
        }
        return createdProductsToIssue;
    }

    public BigDecimal getIssuedQuantityForProductAndOrder(Entity warehouseIssue, Entity product) {
        BigDecimal issueQuantity = BigDecimal.ZERO;
        Entity order = warehouseIssue.getBelongsToField(WarehouseIssueFields.ORDER);
        String hql = String.format(
                "from #productFlowThruDivision_issue as p where p.issued = true and p.warehouseIssue.order.id = %s"
                        + " and p.product.id = %s", order.getId(), product.getId());

        List<Entity> issues = getIssueDD().find(hql).list().getEntities();
        for (Entity is : issues) {
            if (is.getDecimalField("issueQuantity") != null) {
                issueQuantity = issueQuantity.add(is.getDecimalField("issueQuantity"), numberService.getMathContext());
            }
        }
        return issueQuantity;
    }

    public List<Entity> createWarehouseDocumentsForPositions(final ViewDefinitionState view, final List<Entity> issues,
            final Entity locationFrom, final Optional<String> additionalInfo) {

        List<Entity> validDocuments = new ArrayList<>();
        if (!checkIfAllIssueQuantityGraterThanZero(issues)) {
            throw new RuntimeException("productFlowThruDivision.issue.state.accept.error.issueForZero");
        }
        UpdateIssuesLocationsQuantityStatusHolder updateIssuesStatus = tryUpdateIssuesLocationsQuantity(locationFrom, issues);
        if (!updateIssuesStatus.isUpdated()) {
            throw new RuntimeExceptionWithArguments("productFlowThruDivision.issue.state.accept.error.noProductsOnLocation",
                    updateIssuesStatus.getMessage(), locationFrom.getStringField(LocationFields.NUMBER));

        } else {
            MultiMap warehouseIssuesMap = new MultiHashMap();
            for (Entity issue : issues) {
                warehouseIssuesMap.put(issue.getBelongsToField(L_LOCATION).getId(), issue);
            }
            for (Object key : warehouseIssuesMap.keySet()) {
                Long id = (Long) key;
                Entity locationTo = getLocationDD().get(id);
                Collection coll = (Collection) warehouseIssuesMap.get(key);
                CreationDocumentResponse response;
                if (additionalInfo.isPresent()) {
                    response = createWarehouseDocumentForPositions(coll, locationFrom, locationTo, additionalInfo.get());
                } else {
                    response = createWarehouseDocumentForPositions(coll, locationFrom, locationTo);
                }

                if (response.isValid()) {
                    validDocuments.add(response.getDocument());

                } else {
                    if (!response.getErrors().isEmpty()) {
                        response.getErrors().forEach(er -> view.addMessage(er));
                    }
                    throw new RuntimeExceptionWithArguments(
                            "productFlowThruDivision.issue.state.accept.error.documentsNotCreated");
                }
                updateIssuePosition(coll, response.getDocument());
            }
            Set<Entity> warehouseIssues = extractWarehouseIssues(issues);
            updateProductsToIssues(warehouseIssues, issues);
            updateWarehouseIssuesState(warehouseIssues);
            view.addMessage("productFlowThruDivision.issue.documentGeneration.success", ComponentState.MessageType.SUCCESS);
            return validDocuments;
        }
    }

    public void updateProductsToIssues(Set<Entity> warehouseIssues, List<Entity> issues) {
        if (warehouseIssueParameterService.issueForOrder()) {
            updateProductsToIssues(warehouseIssues);
        } else {
            updateProductsToIssuesManual(issues);
        }
    }

    private void updateProductsToIssuesManual(List<Entity> issues) {
        issues.forEach(i -> updateProductToIssueManual(i));
    }

    private void updateProductToIssueManual(Entity issue) {
        Optional<Entity> optionalValue = findProductForIssue(issue);
        if (optionalValue.isPresent()) {
            Entity value = optionalValue.get();
            BigDecimal issueQuantity = value.getDecimalField(ProductsToIssueFields.ISSUE_QUANTITY);
            issueQuantity = BigDecimalUtils.convertNullToZero(issueQuantity).add(
                    issue.getDecimalField(IssueFields.ISSUE_QUANTITY), numberService.getMathContext());
            value.setField(ProductsToIssueFields.ISSUE_QUANTITY, issueQuantity);
            value.getDataDefinition().save(value);
        }
    }

    public Optional<Entity> findProductForIssue(Entity issue) {
        SearchCriteriaBuilder scb = getProductToIssueDD().find();
        scb.add(SearchRestrictions.belongsTo(ProductsToIssueFields.PRODUCT,
                issue.getBelongsToField(ProductsToIssueFields.PRODUCT)));
        scb.add(SearchRestrictions.belongsTo(ProductsToIssueFields.LOCATION,
                issue.getBelongsToField(ProductsToIssueFields.LOCATION)));
        scb.add(SearchRestrictions.belongsTo(ProductsToIssueFields.ADDITIONAL_CODE,
                issue.getBelongsToField(ProductsToIssueFields.ADDITIONAL_CODE)));
        scb.add(SearchRestrictions.belongsTo(ProductsToIssueFields.WAREHOUSE_ISSUE,
                issue.getBelongsToField(ProductsToIssueFields.WAREHOUSE_ISSUE)));

        return Optional.ofNullable(scb.setMaxResults(1).uniqueResult());

    }

    public void updateProductsToIssues(final Set<Entity> warehouseIssues) {
        warehouseIssues.stream().forEach(
                wi -> fillProductsToIssue(wi.getId(),
                        CollectionProducts.fromStringValue(wi.getStringField(WarehouseIssueFields.COLLECTION_PRODUCTS)),
                        ProductsToIssue.parseString(wi.getStringField(WarehouseIssueFields.PRODUCTS_TO_ISSUE_MODE)),
                        wi.getBelongsToField(WarehouseIssueFields.TECHNOLOGY_OPERATION_COMPONENT),
                        wi.getBelongsToField(WarehouseIssueFields.DIVISION)));
    }

    public CreationDocumentResponse createWarehouseDocument(final Entity locationFrom, final Entity locationTo,
            final Entity order, final Collection positions) {
        return createWarehouseDocument(locationFrom, locationTo, order, positions, null);
    }

    public CreationDocumentResponse createWarehouseDocument(final Entity locationFrom, final Entity locationTo,
            final Entity order, final Collection positions, final String additionalInfo) {
        return warehouseIssueDocumentsService.createWarehouseDocument(locationFrom, locationTo, positions, additionalInfo);
    }

    private CreationDocumentResponse createWarehouseDocumentForPositions(final Collection positions, final Entity locationFrom,
            final Entity locationTo) {
        return createWarehouseDocumentForPositions(positions, locationFrom, locationTo, null);
    }

    private CreationDocumentResponse createWarehouseDocumentForPositions(final Collection positions, final Entity locationFrom,
            final Entity locationTo, String additionalInfo) {
        return createWarehouseDocument(locationFrom, locationTo, null, positions, additionalInfo);
    }

    private Set<Entity> extractWarehouseIssues(final List<Entity> issues) {
        Set<Entity> warehouseIssues = Sets.newHashSet();
        for (Entity issue : issues) {
            warehouseIssues.add(issue.getBelongsToField(IssueFields.WAREHOUSE_ISSUE));
        }
        return warehouseIssues;
    }

    private void updateWarehouseIssuesState(final Set<Entity> warehouseIssues) {
        warehouseIssues.forEach(wi -> updateWarehouseIssueState(wi));
    }

    private void updateWarehouseIssueState(Entity warehouseIssue) {
        Entity warehouseIssueState = getWarehouseIssueChangeStateDD().create();
        warehouseIssueState.setField(WarehouseIssueStateChangeFields.DATE_AND_TIME, new Date());
        warehouseIssueState.setField(WarehouseIssueStateChangeFields.SOURCE_STATE,
                warehouseIssue.getStringField(WarehouseIssueFields.STATE));
        warehouseIssueState.setField(WarehouseIssueStateChangeFields.TARGET_STATE, WarehouseIssueState.IN_PROGRESS.toString());
        warehouseIssueState.setField(WarehouseIssueStateChangeFields.STATUS, "03successful");
        warehouseIssueState.setField(WarehouseIssueStateChangeFields.WORKER, securityService.getCurrentUserId());
        warehouseIssueState.setField(WarehouseIssueStateChangeFields.WAREHOUSE_ISSUE, warehouseIssue.getId());
        warehouseIssue.setField(WarehouseIssueFields.STATE, WarehouseIssueState.IN_PROGRESS.getStringValue());
        warehouseIssue.getDataDefinition().save(warehouseIssue);
    }

    public UpdateIssuesLocationsQuantityStatusHolder tryUpdateIssuesLocationsQuantity(final Entity location,
            final List<Entity> issues) {
        Map<Long, BigDecimal> quantities = materialFlowResourcesService.getQuantitiesForProductsAndLocation(
                getUniqueProductsFromIssues(issues), location);
        Map<Long, BigDecimal> originalQuantities = Maps.newHashMap(quantities);
        boolean isValid = true;
        StringBuffer buffer = new StringBuffer();

        for (Entity issue : issues) {
            Entity product = issue.getBelongsToField("product");
            if (location == null) {
                if (buffer.length() != 0) {
                    buffer.append(", ");
                }
                buffer.append(product.getStringField("number"));
                isValid = false;

            } else {
                BigDecimal locationsQuantity = quantities.get(product.getId());
                BigDecimal issueQuantity = issue.getDecimalField("issueQuantity");
                if (locationsQuantity == null || issueQuantity == null || locationsQuantity.compareTo(issueQuantity) == -1) {
                    if (buffer.length() != 0) {
                        buffer.append(", ");
                    }
                    buffer.append(product.getStringField("number"));
                    isValid = false;
                }
                issue.setField("locationsQuantity", originalQuantities.get(product.getId()));
                issue.getDataDefinition().save(issue);
                if (locationsQuantity != null && issueQuantity != null) {
                    quantities.put(product.getId(), locationsQuantity.subtract(issueQuantity));
                }
            }
        }
        return new UpdateIssuesLocationsQuantityStatusHolder(isValid, buffer.toString());
    }

    private DataDefinition getLocationDD() {
        return dataDefinitionService.get(MaterialFlowConstants.PLUGIN_IDENTIFIER, MaterialFlowConstants.MODEL_LOCATION);
    }

    private List<Entity> getUniqueProductsFromIssues(final List<Entity> issues) {
        List<Entity> products = issues.stream().map(issue -> issue.getBelongsToField(L_PRODUCT)).collect(Collectors.toList());
        Map<Long, Entity> distinctProducts = products.stream().collect(Collectors.toMap(p -> p.getId(), p -> p, (p, q) -> p));
        return Lists.newArrayList(distinctProducts.values());
    }

    private DataDefinition getProductionCoutingQuantityDD() {
        return dataDefinitionService.get(BasicProductionCountingConstants.PLUGIN_IDENTIFIER,
                BasicProductionCountingConstants.MODEL_PRODUCTION_COUNTING_QUANTITY);
    }

    private DataDefinition getProductsToIssueDD() {
        return dataDefinitionService.get(ProductFlowThruDivisionConstants.PLUGIN_IDENTIFIER, "productsToIssue");
    }

    private DataDefinition getIssueDD() {
        return dataDefinitionService.get(ProductFlowThruDivisionConstants.PLUGIN_IDENTIFIER, "issue");
    }

    public DataDefinition getwarehouseIssueDD() {
        return dataDefinitionService.get(ProductFlowThruDivisionConstants.PLUGIN_IDENTIFIER,
                ProductFlowThruDivisionConstants.MODEL_WAREHOUSE_ISSUE);
    }

    public DataDefinition getWarehouseIssueChangeStateDD() {
        return dataDefinitionService.get(ProductFlowThruDivisionConstants.PLUGIN_IDENTIFIER,
                ProductFlowThruDivisionConstants.MODEL_WAREHOUSE_ISSUE_STATE_CHANGE);
    }

    private List<Entity> filterCoverageProductsToDivision(final List<Entity> coverageProducts, final Entity divisionEntity) {
        List<Entity> filteredList = Lists.newArrayList();
        for (Entity coverageProduct : coverageProducts) {

            Entity tocDivision = technologyHooksPFTD.getDivisionForOperation(coverageProduct
                    .getBelongsToField(ProductionCountingQuantityFields.TECHNOLOGY_OPERATION_COMPONENT));
            if (tocDivision != null && tocDivision.getId().equals(divisionEntity.getId())) {
                filteredList.add(coverageProduct);
            }
        }

        return filteredList;
    }

    private Multimap<Entity, Entity> groupCoverageProductsByWarehouse(final List<Entity> coverageProducts) {
        Multimap<Entity, Entity> map = ArrayListMultimap.create();
        for (Entity coverageProduct : coverageProducts) {
            map.put(coverageProduct.getBelongsToField(ProductionCountingQuantityFieldsPFTD.COMPONENTS_LOCATION), coverageProduct);
        }
        return map;
    }

    private Entity findExistingProductToIssue(final Entity product, final Entity warehouseIssue, final Entity componentsLocation) {
        return getProductsToIssueDD().find().add(SearchRestrictions.belongsTo("product", product))
                .add(SearchRestrictions.belongsTo("warehouseIssue", warehouseIssue))
                .add(SearchRestrictions.belongsTo(L_LOCATION, componentsLocation)).setMaxResults(1).uniqueResult();
    }

    public boolean checkIfAllIssueQuantityGraterThanZero(final List<Entity> issues) {
        if (!issues.stream().filter(e -> e.getDecimalField(IssueFields.ISSUE_QUANTITY).compareTo(BigDecimal.ZERO) == 0)
                .collect(Collectors.toList()).isEmpty()) {
            return false;
        }
        return true;
    }

    private DataDefinition getProductToIssueDD() {
        return dataDefinitionService.get(ProductFlowThruDivisionConstants.PLUGIN_IDENTIFIER,
                ProductFlowThruDivisionConstants.MODEL_PRODUCTS_TO_ISSUE);
    }

    private void reCalculateDemandQuantity(final Entity productToIssue) {
        BigDecimal conversion = productToIssue.getDecimalField(ProductsToIssueFields.CONVERSION);
        Entity product = productToIssue.getBelongsToField(ProductsToIssueFields.PRODUCT);
        String unit = product.getStringField(ProductFields.UNIT);
        String additionalUnit = product.getStringField(ProductFields.ADDITIONAL_UNIT);

        if (conversion == null) {
            if (!StringUtils.isEmpty(additionalUnit)) {
                conversion = getConversion(product, unit, additionalUnit);
            } else {
                conversion = BigDecimal.ONE;
            }
        }
        BigDecimal demandQuantity = productToIssue.getDecimalField(ProductsToIssueFields.DEMAND_QUANTITY);
        if (demandQuantity != null) {
            BigDecimal newAdditionalQuantity = demandQuantity.multiply(conversion, numberService.getMathContext());
            newAdditionalQuantity = newAdditionalQuantity.setScale(NumberService.DEFAULT_MAX_FRACTION_DIGITS_IN_DECIMAL,
                    RoundingMode.HALF_UP);
            productToIssue.setField(ProductsToIssueFields.CONVERSION, conversion);
            productToIssue.setField(ProductsToIssueFields.ADDITIONAL_DEMAND_QUANTITY, newAdditionalQuantity);
        }
    }

    private BigDecimal getConversion(Entity product, String unit, String additionalUnit) {
        PossibleUnitConversions unitConversions = unitConversionService.getPossibleConversions(unit,
                searchCriteriaBuilder -> searchCriteriaBuilder.add(SearchRestrictions.belongsTo(
                        UnitConversionItemFieldsB.PRODUCT, product)));
        if (unitConversions.isDefinedFor(additionalUnit)) {
            return unitConversions.asUnitToConversionMap().get(additionalUnit);
        } else {
            return BigDecimal.ONE;
        }
    }

    public void updateIssuePosition(List<Entity> issues, Entity document) {
        issues.forEach(entity -> {
            entity.setField(IssueFields.ISSUED, true);
            entity.getDataDefinition().save(entity);
        });
    }

    public void updateIssuePosition(Collection coll, Entity document) {
        coll.forEach(ob -> {
            Entity entity = (Entity) ob;
            entity.setField(IssueFields.DATE_OF_ISSUED, DateTime.now().toDate());
            entity.setField(IssueFields.ISSUED, true);
            entity.setField(IssueFields.DOCUMENT, document);
            entity.getDataDefinition().save(entity);
        });
    }


    @Autowired
    private MaterialFlowService materialFlowService;

    //RicefishDev
    private List<Entity> createProductIssueEntryForOrderFinalProduct(final Entity warehouseIssue, final Entity order) {
        List<Entity> coverageProducts = getProductionCoutingQuantityDD()
                .find()
                .add(SearchRestrictions.belongsTo(ProductionCountingQuantityFields.ORDER, order))
                .add(SearchRestrictions.eq(ProductionCountingQuantityFields.ROLE,
                        ProductionCountingQuantityRole.PRODUCED.getStringValue()))
                .add(SearchRestrictions.eq(ProductionCountingQuantityFields.TYPE_OF_MATERIAL,
                        ProductionCountingQuantityTypeOfMaterial.FINAL_PRODUCT.getStringValue())).list().getEntities();

       // Entity finalLocation = warehouseIssue.getBelongsToField(WarehouseIssueFields.PLACE_OF_ISSUE);

        Entity finalLocation = materialFlowService.getLocationByName("成品库");

        Map<Long, BigDecimal> quantitiesForProducts = materialFlowResourcesService.getQuantitiesForProductsAndLocation(
                coverageProducts.stream()
                        .map(coverageProduct -> coverageProduct.getBelongsToField(ProductionCountingQuantityFields.PRODUCT))
                        .distinct().collect(Collectors.toList()),
                finalLocation);
        return createFinalProductForIssueEntry(coverageProducts, warehouseIssue, quantitiesForProducts,finalLocation);
    }


    public List<Entity> createFinalProductForIssueEntry(final List<Entity> coverageProducts, Entity warehouseIssue,
                                                   final Map<Long, BigDecimal> quantities,final Entity finalLocation) {
        Multimap<Entity, Entity> groupedCoverageProducts = groupCoverageFinalProductsByWarehouse(coverageProducts,finalLocation);
        List<Entity> createdProductsToIssue = Lists.newArrayList();
        for (Entity warehouse : groupedCoverageProducts.keySet()) {
            Collection<Entity> coverageProductsForWarehouse = groupedCoverageProducts.get(warehouse);

            Map<Long, BigDecimal> quantitiesForProductsInWarehouse = materialFlowResourcesService
                    .getQuantitiesForProductsAndLocation(
                            coverageProductsForWarehouse
                                    .stream()
                                    .map(coverageProduct -> coverageProduct
                                            .getBelongsToField(ProductionCountingQuantityFields.PRODUCT))
                                    .collect(Collectors.toList()), warehouse);
            for (Entity coverageProduct : coverageProductsForWarehouse) {
                Entity product = coverageProduct.getBelongsToField(ProductionCountingQuantityFields.PRODUCT);

                Entity productToIssue = findExistingProductToIssue(product, warehouseIssue, finalLocation);
                if (productToIssue == null) {
                    productToIssue = getProductsToIssueDD().create();
                    productToIssue.setField(ProductsToIssueFields.ISSUED, false);
                    productToIssue.setField("warehouseIssue", warehouseIssue);
                    productToIssue.setField("product", product);
                    productToIssue.setField("demandQuantity",
                            coverageProduct.getDecimalField(ProductionCountingQuantityFields.PLANNED_QUANTITY));
                } else {

                    productToIssue.setField(
                            "demandQuantity",
                            coverageProduct.getDecimalField(ProductionCountingQuantityFields.PLANNED_QUANTITY).add(
                                    productToIssue.getDecimalField("demandQuantity")));
                }
                Entity location = warehouseIssue.getBelongsToField(WarehouseIssueFields.PLACE_OF_ISSUE);
                BigDecimal resourceQuantity = BigDecimal.ZERO;
                if (quantities == null || quantities.size() == 0) {
                    resourceQuantity = materialFlowResourcesService.getResourcesQuantityForLocationAndProduct(location, product);
                } else {
                    resourceQuantity = quantities.get(product.getId());
                }

                productToIssue.setField("locationsQuantity", resourceQuantity);

                Entity opic = dataDefinitionService
                        .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_OPERATION_PRODUCT_OUT_COMPONENT)
                        .find()
                        .add(SearchRestrictions.belongsTo("operationComponent", coverageProduct
                                .getBelongsToField(ProductionCountingQuantityFields.TECHNOLOGY_OPERATION_COMPONENT)))
                        .add(SearchRestrictions.belongsTo("product", BasicConstants.PLUGIN_IDENTIFIER,
                                BasicConstants.MODEL_PRODUCT, product.getId())).setMaxResults(1).uniqueResult();
                productToIssue.setField("productInComponent", opic);
                Entity location2 = materialFlowService.getLocationByName("中转库");

                productToIssue.setField(L_LOCATION, location2);

                productToIssue.setField("placeOfIssueQuantity", quantitiesForProductsInWarehouse.get(product.getId()));

                BigDecimal issueQuantity = BigDecimal.ZERO;

                issueQuantity = getIssuedQuantityForProductAndOrder(warehouseIssue, product);
                productToIssue.setField("issueQuantity", issueQuantity);
                reCalculateDemandQuantity(productToIssue);
                createdProductsToIssue.add(productToIssue.getDataDefinition().save(productToIssue));
            }
        }
        return createdProductsToIssue;
    }

    private Multimap<Entity, Entity> groupCoverageFinalProductsByWarehouse(final List<Entity> coverageProducts,final Entity finalLocation) {
        Multimap<Entity, Entity> map = ArrayListMultimap.create();
        for (Entity coverageProduct : coverageProducts) {
            map.put(finalLocation, coverageProduct);
        }
        return map;
    }

}
