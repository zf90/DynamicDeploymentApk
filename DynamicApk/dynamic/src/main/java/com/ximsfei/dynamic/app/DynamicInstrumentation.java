package com.ximsfei.dynamic.app;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;

import com.ximsfei.dynamic.DynamicApkManager;
import com.ximsfei.dynamic.util.DynamicConstants;
import com.ximsfei.dynamic.pm.DynamicApkParser;
import com.ximsfei.dynamic.reflect.Reflect;

import java.util.List;

/**
 * Created by pengfenx on 3/2/2016.
 */
public class DynamicInstrumentation extends Instrumentation {
    private final Instrumentation mBase;
    private final Reflect mContextImplReflect;
    private final Reflect mActivityReflect;
    private final Reflect mInstrumentationReflect;

    public DynamicInstrumentation(Instrumentation base) {
        mBase = base;
        mContextImplReflect = Reflect.create("android.app.ContextImpl");
        mActivityReflect = Reflect.create(Activity.class);
        mInstrumentationReflect = Reflect.create(Instrumentation.class);
    }

    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, Bundle bundle, UserHandle user) {
        ActivityInfo ai = resolveActivity(intent, intent.resolveType(who), DynamicConstants.RESOLVE_ACTIVITY);

        if (ai != null) {
            intent.putExtra(DynamicConstants.DYNAMIC_ACTIVITY_FLAG, ai.name);
            intent.setClassName(DynamicActivityThread.getInstance().getHostPackageName(),
                    DynamicConstants.STUB_DYNAMIC_ACTIVITY);
        }
        try {
            return (ActivityResult) mInstrumentationReflect.setMethod("execStartActivity",
                    Context.class, IBinder.class, IBinder.class, Activity.class, Intent.class,
                    int.class, Bundle.class, UserHandle.class)
                    .invoke(mBase, who, contextThread, token, target, intent, requestCode, bundle, user);
        } catch (Reflect.ReflectException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, String target,
            Intent intent, int requestCode, Bundle bundle) {
        ActivityInfo ai = resolveActivity(intent, intent.resolveType(who), DynamicConstants.RESOLVE_ACTIVITY);

        if (ai != null) {
            intent.putExtra(DynamicConstants.DYNAMIC_ACTIVITY_FLAG, ai.name);
            intent.setClassName(DynamicActivityThread.getInstance().getHostPackageName(),
                    DynamicConstants.STUB_DYNAMIC_ACTIVITY);
        }
        try {
            return (ActivityResult) mInstrumentationReflect.setMethod("execStartActivity",
                    Context.class, IBinder.class, IBinder.class, String.class, Intent.class, int.class, Bundle.class)
                    .invoke(mBase, who, contextThread, token, target, intent, requestCode, bundle);
        } catch (Reflect.ReflectException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Activity target,
                                            Intent intent, int requestCode, Bundle bundle) {
        ActivityInfo ai = resolveActivity(intent, intent.resolveType(who), DynamicConstants.RESOLVE_ACTIVITY);

        if (ai != null) {
            intent.putExtra(DynamicConstants.DYNAMIC_ACTIVITY_FLAG, ai.name);
            intent.setClassName(DynamicActivityThread.getInstance().getHostPackageName(),
                    DynamicConstants.STUB_DYNAMIC_ACTIVITY);
        }
        try {
            return (ActivityResult) mInstrumentationReflect.setMethod("execStartActivity",
                    Context.class, IBinder.class, IBinder.class, Activity.class, Intent.class, int.class, Bundle.class)
                    .invoke(mBase, who, contextThread, token, target, intent, requestCode, bundle);
        } catch (Reflect.ReflectException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        String clsName = intent.getStringExtra(DynamicConstants.DYNAMIC_ACTIVITY_FLAG);
        if (clsName != null) className = clsName;
        return super.newActivity(cl, className, intent);
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        super.callApplicationOnCreate(app);
    }

        @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        String clsName = activity.getIntent().getStringExtra(DynamicConstants.DYNAMIC_ACTIVITY_FLAG);
        DynamicApkParser.Activity a = (DynamicApkParser.Activity)
                DynamicApkManager.getInstance()
                        .queryClassName(clsName,
                                DynamicConstants.RESOLVE_ACTIVITY);
        if (a != null) hookContext(a, activity);

        super.callActivityOnCreate(activity, icicle);
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
    }

    @Override
    public void callActivityOnDestroy(Activity activity) {
        super.callActivityOnDestroy(activity);
    }

    private ActivityInfo resolveActivity(Intent intent, String resolvedType, int resolvedFlag) {
        List<ResolveInfo> resolveInfos = DynamicApkManager.getInstance()
                .resolveIntent(intent, resolvedType, resolvedFlag);
        if (resolveInfos.size() != 0) {
            return resolveInfos.get(0).activityInfo;
        }
        return null;
    }

    private void hookContext(DynamicApkParser.Activity a, Activity activity) {
        try {
            mContextImplReflect.setField("mResources")
                    .set(activity.getBaseContext(), a.owner.resources);
            changeTheme(a, mContextImplReflect, activity.getBaseContext());
        } catch (Reflect.ReflectException e) {
            e.printStackTrace();
        }
        hookActivity(a, activity);
    }

    private void hookActivity(DynamicApkParser.Activity a, Activity activity) {
        DynamicContextImpl dynamicContext = new DynamicContextImpl(activity.getBaseContext(), a.owner);
        try {
            mActivityReflect.setField("mResources")
                    .set(activity, dynamicContext.getResources());
            mActivityReflect.setField("mBase")
                    .set(activity, dynamicContext);
            mActivityReflect.setField("mApplication")
                    .set(activity, a.owner.application);
            mActivityReflect.setField("mActivityInfo")
                    .set(activity, a.info);
            changeTheme(a, mActivityReflect, activity);
        } catch (Reflect.ReflectException e) {
            e.printStackTrace();
        }
        changeTitle(a, activity);
    }

    private void changeTheme(DynamicApkParser.Activity a, Reflect r, Object o)
            throws Reflect.ReflectException {
        if (a.info.getThemeResource() != 0) {
            Resources.Theme theme = a.owner.resources.newTheme();
            theme.applyStyle(a.info.getThemeResource(), true);
            r.setField("mTheme").set(o, theme);
        }
    }

    private void changeTitle(DynamicApkParser.Activity a, Activity activity) {
        if (a.info.labelRes != 0) {
            activity.setTitle(a.owner.resources.getString(a.info.labelRes));
        } else if (a.owner.applicationInfo.nonLocalizedLabel != null) {
            activity.setTitle(a.owner.applicationInfo.nonLocalizedLabel);
        } else if (a.owner.applicationInfo.labelRes != 0) {
            activity.setTitle(a.owner.resources.getString(a.owner.applicationInfo.labelRes));
        }
    }
}