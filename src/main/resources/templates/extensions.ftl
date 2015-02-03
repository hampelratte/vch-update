<#include "header.ftl">
<#include "status_messages.ftl">
<#include "navigation.ftl">

<h1>${I18N_EXTENSIONS}</h1>
<div id="container">
    <div id="tabs" class="ui-tabs ui-corner-all" style="float:left; min-width:455px">
        <ul>
            <li><a href="${ACTION}?tab=installed"><span>${I18N_BUNDLES_INSTALLED}</span></a></li>
            <li><a href="${ACTION}?tab=available"><span>${I18N_BUNDLES_AVAILABLE}</span></a></li>
        </ul>
    </div>
    <fieldset style="float:left; border:1px solid #AAA; padding:0px 20px 0px 20px; margin:0px; margin-left:40px; height: 409px;" class="ui-corner-all">
        <legend style="padding-top:0px">${I18N_DESCRIPTION}</legend>
        <table>
            <tr><td>${I18N_NAME}</td><td id="bundle_name"></td></tr>
            <tr><td>${I18N_VERSION}</td><td id="bundle_version"></td></tr>
            <tr><td>${I18N_AUTHOR}</td><td id="bundle_author"></td></tr>
            <tr><td>${I18N_STATE}</td><td id="bundle_state"></td></tr>
            <tr><td colspan="2">${I18N_DESCRIPTION}:<br/>
                <textarea readonly="readonly" id="bundle_description" cols="40" rows="11" class="ui-widget ui-widget-content ui-corner-all"></textarea>
                </td>
            </tr>
        </table>
    </fieldset>
    <div style="clear:both"></div>
</div>

<script type="text/javascript">
    $(document).ready(function() {
        // enable tabs
        $('#tabs').tabs({
            spinner: '<img border="0" src="${STATIC_PATH}/indicator.gif" alt=""/> ${I18N_LOADING_DATA}', 
            ajaxOptions: {
                error: function(request, textStatus, exception) {
                    $.pnotify( {
                        pnotify_title : request.statusText,
                        pnotify_text : request.responseText,
                        pnotify_type : 'error'
                    });
                    $('#tabs').tabs('abort');
                } 
            } 
        }); 
        $('#tabs').removeClass('ui-widget');
        
        // tweak the look
        $('#tabs').removeClass('ui-widget-content');
        $('.ui-tabs-panel').css('border-left', '1px solid #AAA');
        $('.ui-tabs-panel').css('border-right', '1px solid #AAA');
        $('.ui-tabs-panel').css('border-bottom', '1px solid #AAA');
        $('.ui-tabs-nav').removeClass('ui-corner-all');
        $('.ui-tabs-nav').addClass('ui-corner-top');
        $('.ui-tabs').css('padding', '0px');
    });
  

    function showDetails(name, author, version, state, description) {
        $('#bundle_name').text(name);
        $('#bundle_version').text(version);
        $('#bundle_author').text(author);
        $('#bundle_state').text(state);
        $('#bundle_description').text(description);
    }
</script>
<#include "footer.ftl">