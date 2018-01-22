/*<![CDATA[*/
/** Below the javascript/ajax/jquery code to generate and display the stats api results.
* By default api will show stats of the previous day
* user can generate stats via filter elements on the html page */


/**
* Execute  getGraphData function when onchange event is fired 
* on the filter input values of datetimepicker, downsampling and menulist.
*/

var flowid = window.location.href.split("#")[1];
var downsampling = "10m";
var graphInterval;

$(function() {
		
		$("#datetimepicker7,#datetimepicker8,#menulist,#autoreload").on("change",function() {
			getGraphData();
		});
	});
	


/**
* Execute this function when page is loaded
* or when user is directed to this page.
*/
$(document).ready(function() {

	
	$.datetimepicker.setLocale('en');
	var date = new Date()
	var yesterday = new Date(date.getTime());
	yesterday.setDate(date.getDate() - 1);
	var YesterDayDate = moment(yesterday).format("YYYY/MM/DD HH:mm:ss");
	var EndDate = moment(date).format("YYYY/MM/DD HH:mm:ss");
	var convertedStartDate = moment(YesterDayDate).format("YYYY-MM-DD-HH:mm:ss");
	var convertedEndDate = moment(EndDate).format("YYYY-MM-DD-HH:mm:ss");	
	$("#datetimepicker7").val(YesterDayDate);	
	$("#datetimepicker8").val(EndDate);
	$('#datetimepicker7').datetimepicker({
		  format:'Y/m/d h:i:s',
	});
	$('#datetimepicker8').datetimepicker({
		  format:'Y/m/d h:i:s',
	});
	$('#datetimepicker_dark').datetimepicker({theme:'dark'})
	
	loadGraph.loadGraphData("/stats/flowid/"+flowid+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/pen.flow.packets","GET").then(function(response) {
		showStatsGraph.showStatsData(response); 
	})
})


/**
* Execute this function to  show stats data whenever user filters data in the
* html page.
*/
function getGraphData() {
	

	var regex = new RegExp("^\\d+(s|h|m){1}$");
	var currentDate = new Date();
	var startDate = new Date($("#datetimepicker7").val());
	var endDate =  new Date($("#datetimepicker8").val());
	var convertedStartDate = moment(startDate).format("YYYY-MM-DD-HH:mm:ss");
	var convertedEndDate = moment(endDate).format("YYYY-MM-DD-HH:mm:ss");		
	var selMetric=$("select.selectbox_menulist").val();
	var valid=true;
		
	if(startDate.getTime() > currentDate.getTime()) {
		common.infoMessage('startDate should not be greater than currentDate.','error');		
		valid=false;
		return;
	} else if(endDate.getTime() < startDate.getTime()){
		common.infoMessage('endDate should not be less than startDate','error');
		valid=false;
		return;
	}
	
	var autoreload = $("#autoreload").val();
	var numbers = /^[-+]?[0-9]+$/;  
	var checkNo = $("#autoreload").val().match(numbers);
	var checkbox =  $("#check").prop("checked");
	
	if(valid){

		
		loadGraph.loadGraphData("/stats/flowid/"+flowid+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/"+selMetric,"GET").then(function(response) {
			showStatsGraph.showStatsData(response); 
		})
				
			try {
				clearInterval(graphInterval);
			} catch(err) {

			}
			
			if(autoreload){
				graphInterval = setInterval(function(){
					callIntervalData() 
				}, 1000*autoreload);
			}
	}
}

function callIntervalData() {
	
	var currentDate = new Date();
	var startDate = new Date($("#datetimepicker7").val());
	var convertedStartDate = moment(startDate).format("YYYY-MM-DD-HH:mm:ss");
	var endDate = new Date()
	var convertedEndDate = moment(endDate).format("YYYY-MM-DD-HH:mm:ss");	
	var selMetric=$("select.selectbox_menulist").val();
		
	
	loadGraph.loadGraphData("/stats/flowid/"+flowid+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/"+selMetric,"GET").then(function(response) {
		showStatsGraph.showStatsData(response); 
	})
}

/* ]]> */