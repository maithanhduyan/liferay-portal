package com.ext.portlet.reports.service.persistence;

import com.liferay.portal.model.ModelListener;
import com.liferay.portal.util.PropsUtil;

import com.liferay.util.GetterUtil;
import com.liferay.util.Validator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class ReportsEntryUtil {
    private static final String _UTIL = ReportsEntryUtil.class.getName();
    private static final String _LISTENER = GetterUtil.getString(PropsUtil.get(
                "value.object.listener.com.ext.portlet.reports.model.ReportsEntry"));
    private static Log _log = LogFactory.getLog(ReportsEntryUtil.class);
    private static ReportsEntryUtil _util;
    private ReportsEntryPersistence _persistence;

    public static com.ext.portlet.reports.model.ReportsEntry create(
        java.lang.String entryId) {
        return getPersistence().create(entryId);
    }

    public static com.ext.portlet.reports.model.ReportsEntry remove(
        java.lang.String entryId)
        throws com.liferay.portal.SystemException, 
            com.ext.portlet.reports.NoSuchEntryException {
        ModelListener listener = _getListener();

        if (listener != null) {
            listener.onBeforeRemove(findByPrimaryKey(entryId));
        }

        com.ext.portlet.reports.model.ReportsEntry reportsEntry = getPersistence()
                                                                      .remove(entryId);

        if (listener != null) {
            listener.onAfterRemove(reportsEntry);
        }

        return reportsEntry;
    }

    public static com.ext.portlet.reports.model.ReportsEntry remove(
        com.ext.portlet.reports.model.ReportsEntry reportsEntry)
        throws com.liferay.portal.SystemException {
        ModelListener listener = _getListener();

        if (listener != null) {
            listener.onBeforeRemove(reportsEntry);
        }

        reportsEntry = getPersistence().remove(reportsEntry);

        if (listener != null) {
            listener.onAfterRemove(reportsEntry);
        }

        return reportsEntry;
    }

    public static com.ext.portlet.reports.model.ReportsEntry update(
        com.ext.portlet.reports.model.ReportsEntry reportsEntry)
        throws com.liferay.portal.SystemException {
        ModelListener listener = _getListener();
        boolean isNew = reportsEntry.isNew();

        if (listener != null) {
            if (isNew) {
                listener.onBeforeCreate(reportsEntry);
            } else {
                listener.onBeforeUpdate(reportsEntry);
            }
        }

        reportsEntry = getPersistence().update(reportsEntry);

        if (listener != null) {
            if (isNew) {
                listener.onAfterCreate(reportsEntry);
            } else {
                listener.onAfterUpdate(reportsEntry);
            }
        }

        return reportsEntry;
    }

    public static com.ext.portlet.reports.model.ReportsEntry update(
        com.ext.portlet.reports.model.ReportsEntry reportsEntry,
        boolean saveOrUpdate) throws com.liferay.portal.SystemException {
        ModelListener listener = _getListener();
        boolean isNew = reportsEntry.isNew();

        if (listener != null) {
            if (isNew) {
                listener.onBeforeCreate(reportsEntry);
            } else {
                listener.onBeforeUpdate(reportsEntry);
            }
        }

        reportsEntry = getPersistence().update(reportsEntry, saveOrUpdate);

        if (listener != null) {
            if (isNew) {
                listener.onAfterCreate(reportsEntry);
            } else {
                listener.onAfterUpdate(reportsEntry);
            }
        }

        return reportsEntry;
    }

    public static com.ext.portlet.reports.model.ReportsEntry findByPrimaryKey(
        java.lang.String entryId)
        throws com.liferay.portal.SystemException, 
            com.ext.portlet.reports.NoSuchEntryException {
        return getPersistence().findByPrimaryKey(entryId);
    }

    public static com.ext.portlet.reports.model.ReportsEntry fetchByPrimaryKey(
        java.lang.String entryId) throws com.liferay.portal.SystemException {
        return getPersistence().fetchByPrimaryKey(entryId);
    }

    public static java.util.List findByCompanyId(java.lang.String companyId)
        throws com.liferay.portal.SystemException {
        return getPersistence().findByCompanyId(companyId);
    }

    public static java.util.List findByCompanyId(java.lang.String companyId,
        int begin, int end) throws com.liferay.portal.SystemException {
        return getPersistence().findByCompanyId(companyId, begin, end);
    }

    public static java.util.List findByCompanyId(java.lang.String companyId,
        int begin, int end, com.liferay.portal.kernel.util.OrderByComparator obc)
        throws com.liferay.portal.SystemException {
        return getPersistence().findByCompanyId(companyId, begin, end, obc);
    }

    public static com.ext.portlet.reports.model.ReportsEntry findByCompanyId_First(
        java.lang.String companyId,
        com.liferay.portal.kernel.util.OrderByComparator obc)
        throws com.liferay.portal.SystemException, 
            com.ext.portlet.reports.NoSuchEntryException {
        return getPersistence().findByCompanyId_First(companyId, obc);
    }

    public static com.ext.portlet.reports.model.ReportsEntry findByCompanyId_Last(
        java.lang.String companyId,
        com.liferay.portal.kernel.util.OrderByComparator obc)
        throws com.liferay.portal.SystemException, 
            com.ext.portlet.reports.NoSuchEntryException {
        return getPersistence().findByCompanyId_Last(companyId, obc);
    }

    public static com.ext.portlet.reports.model.ReportsEntry[] findByCompanyId_PrevAndNext(
        java.lang.String entryId, java.lang.String companyId,
        com.liferay.portal.kernel.util.OrderByComparator obc)
        throws com.liferay.portal.SystemException, 
            com.ext.portlet.reports.NoSuchEntryException {
        return getPersistence().findByCompanyId_PrevAndNext(entryId, companyId,
            obc);
    }

    public static java.util.List findByUserId(java.lang.String userId)
        throws com.liferay.portal.SystemException {
        return getPersistence().findByUserId(userId);
    }

    public static java.util.List findByUserId(java.lang.String userId,
        int begin, int end) throws com.liferay.portal.SystemException {
        return getPersistence().findByUserId(userId, begin, end);
    }

    public static java.util.List findByUserId(java.lang.String userId,
        int begin, int end, com.liferay.portal.kernel.util.OrderByComparator obc)
        throws com.liferay.portal.SystemException {
        return getPersistence().findByUserId(userId, begin, end, obc);
    }

    public static com.ext.portlet.reports.model.ReportsEntry findByUserId_First(
        java.lang.String userId,
        com.liferay.portal.kernel.util.OrderByComparator obc)
        throws com.liferay.portal.SystemException, 
            com.ext.portlet.reports.NoSuchEntryException {
        return getPersistence().findByUserId_First(userId, obc);
    }

    public static com.ext.portlet.reports.model.ReportsEntry findByUserId_Last(
        java.lang.String userId,
        com.liferay.portal.kernel.util.OrderByComparator obc)
        throws com.liferay.portal.SystemException, 
            com.ext.portlet.reports.NoSuchEntryException {
        return getPersistence().findByUserId_Last(userId, obc);
    }

    public static com.ext.portlet.reports.model.ReportsEntry[] findByUserId_PrevAndNext(
        java.lang.String entryId, java.lang.String userId,
        com.liferay.portal.kernel.util.OrderByComparator obc)
        throws com.liferay.portal.SystemException, 
            com.ext.portlet.reports.NoSuchEntryException {
        return getPersistence().findByUserId_PrevAndNext(entryId, userId, obc);
    }

    public static java.util.List findWithDynamicQuery(
        com.liferay.portal.kernel.dao.DynamicQueryInitializer queryInitializer)
        throws com.liferay.portal.SystemException {
        return getPersistence().findWithDynamicQuery(queryInitializer);
    }

    public static java.util.List findWithDynamicQuery(
        com.liferay.portal.kernel.dao.DynamicQueryInitializer queryInitializer,
        int begin, int end) throws com.liferay.portal.SystemException {
        return getPersistence().findWithDynamicQuery(queryInitializer, begin,
            end);
    }

    public static java.util.List findAll()
        throws com.liferay.portal.SystemException {
        return getPersistence().findAll();
    }

    public static java.util.List findAll(int begin, int end)
        throws com.liferay.portal.SystemException {
        return getPersistence().findAll(begin, end);
    }

    public static java.util.List findAll(int begin, int end,
        com.liferay.portal.kernel.util.OrderByComparator obc)
        throws com.liferay.portal.SystemException {
        return getPersistence().findAll(begin, end, obc);
    }

    public static void removeByCompanyId(java.lang.String companyId)
        throws com.liferay.portal.SystemException {
        getPersistence().removeByCompanyId(companyId);
    }

    public static void removeByUserId(java.lang.String userId)
        throws com.liferay.portal.SystemException {
        getPersistence().removeByUserId(userId);
    }

    public static void removeAll() throws com.liferay.portal.SystemException {
        getPersistence().removeAll();
    }

    public static int countByCompanyId(java.lang.String companyId)
        throws com.liferay.portal.SystemException {
        return getPersistence().countByCompanyId(companyId);
    }

    public static int countByUserId(java.lang.String userId)
        throws com.liferay.portal.SystemException {
        return getPersistence().countByUserId(userId);
    }

    public static int countAll() throws com.liferay.portal.SystemException {
        return getPersistence().countAll();
    }

    public static ReportsEntryPersistence getPersistence() {
        return _getUtil()._persistence;
    }

    public void setPersistence(ReportsEntryPersistence persistence) {
        _persistence = persistence;
    }

    private static ReportsEntryUtil _getUtil() {
        if (_util == null) {
            _util = (ReportsEntryUtil) com.liferay.portal.kernel.bean.BeanLocatorUtil.locate(_UTIL);
        }

        return _util;
    }

    private static ModelListener _getListener() {
        if (Validator.isNotNull(_LISTENER)) {
            try {
                return (ModelListener) Class.forName(_LISTENER).newInstance();
            } catch (Exception e) {
                _log.error(e);
            }
        }

        return null;
    }
}
