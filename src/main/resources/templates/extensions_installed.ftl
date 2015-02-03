<form id="installed_form" action="${ACTION}" method="get">
<div style="overflow: hidden">
    <select id="installed" name="installed" size="18" style="width: 100%" multiple="multiple">
        <#list INSTALLED as bundle>
            <#if bundle.state == 1>
                <#assign cls="stopped">
                <#assign state=I18N_UNINSTALLED>
            <#elseif bundle.state == 2>
                <#assign cls="stopped">
                <#assign state=I18N_INSTALLED>
            <#elseif bundle.state == 4>
                <#assign cls="stopped">
                <#assign state=I18N_RESOLVED>
            <#elseif bundle.state == 8>
                <#assign cls="stopped">
                <#assign state=I18N_STARTING>
            <#elseif bundle.state == 16>
                <#assign cls="started">
                <#assign state=I18N_STOPPING>
            <#elseif bundle.state == 32>
                <#assign cls="started">
                <#assign state=I18N_ACTIVE>
            </#if>
            <option vch:bundle-symbolicname="${bundle.symbolicName}" vch:bundle-version="${bundle.version}" class="${cls}" value="${bundle.bundleId}" onclick="showDetails('${bundle.name}', '${bundle.author}', '${bundle.version}', '${state}', '${bundle.description}')">
                ${bundle.name} (${bundle.version})
            </option> 
        </#list>
    </select>
</div>
<br/>
<input id="button_update" class="ui-button" type="submit" name="submit_update" value="${I18N_UPDATE}"/>
<input id="button_start" class="ui-button" type="submit" name="submit_start" value="${I18N_START}"/>
<input id="button_stop" class="ui-button" type="submit" name="submit_stop" value="${I18N_STOP}"/>
<input id="button_uninstall" class="ui-button" type="submit" name="submit_uninstall" value="${I18N_UNINSTALL}"/>
</form>

<script type="text/javascript">
    $(document).ready(function() {
        var notice = $.pnotify({
            pnotify_title: '${I18N_INFO}',
            pnotify_text : '${I18N_UPDATES_SEARCHING}',
            pnotify_notice_icon: 'icon ui-icon-loading',
            pnotify_hide: false,
            pnotify_closer: false}
        );
        $.ajax({
            type: "GET",
            url: "${ACTION}",
            data: "updates",
            dataType: "json",
            success: function(data) {
                var updates_available=false;
                for(var i=0; i<data.length; i++) {
                    var res = data[i];
                    var name = res.symbolicName;
                    var version = res.version;
                    var options = $('#installed option');
                    for(var j=0; j<options.length; j++) {
                        var option = options[j];
                        if($(option).attr('vch:bundle-symbolicname') == name) {
                            if($(option).attr('vch:bundle-version') != version) {
                                $(option).addClass('ui-state-highlight');
                                $(option).text($(option).text() + ' - ${I18N_UPDATES_AVAILABLE} - ${I18N_VERSION}:' + version);
                                updates_available = true;
                            } 
                        }
                    }
                }
                if(updates_available) {
                    var options = {
                        pnotify_title : '${I18N_UPDATES_AVAILABLE}',
                        pnotify_text : '${I18N_UPDATES_AVAILABLE_TEXT}',
                        pnotify_hide : true,
                        pnotify_closer : true,
                        pnotify_notice_icon : 'ui-icon ui-icon-info'
                    };
                    notice.pnotify(options);
                    notice.effect('bounce');
                } else {
                    var options = {
                        pnotify_title : '${I18N_INFO}',
                        pnotify_text : '${I18N_NO_UPDATES_AVAILABLE}',
                        pnotify_hide : true,
                        pnotify_closer : true,
                        pnotify_notice_icon : 'ui-icon ui-icon-info'
                    };
                    notice.pnotify(options);
                    notice.effect('bounce');
                }
            },
            error: function(request, textStatus, exception){
                var options = {
                    pnotify_title : request.statusText,
                    pnotify_text : request.responseText,
                    pnotify_type : 'error',
                    pnotify_closer : true,
                    pnotify_hide: false
                };
                notice.pnotify(options);
                notice.effect('bounce');
            }
        });
    });
    
    // enable button hover and click effect
    $(function() {
        $('.ui-button').button();
        $('.ui-button').removeClass('ui-widget');
    });
</script>