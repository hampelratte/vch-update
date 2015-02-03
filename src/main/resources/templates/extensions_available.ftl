<form id="available_form" action="${ACTION}" method="get">
<div style="overflow: hidden">
    <select name="available" size="18" style="width: 100%" multiple="multiple">
        <#list AVAILABLE as resource>
        <option value="${resource.symbolicName}" onclick="showDetails('${resource.presentationName}', '${resource.author}', '${resource.version}', 'not installed', '${resource.description}')">
            ${resource.presentationName} (${resource.version})
        </option> 
        </#list>
    </select>
</div>
<br/>
<input id="button_install" class="ui-button" type="submit" name="submit_install" value="${I18N_INSTALL}"/>
</form>
<script type="text/javascript">
    // enable button hover and click effect
    $(function() {
        $('.ui-button').button();
        $('.ui-button').removeClass('ui-widget');
    });
</script>