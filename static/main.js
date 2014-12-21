$(document).ready(function() {
  $('.hidden').hide().removeClass('hidden');

  if ($('.kill-button').length > 0 && navigator.geolocation) {
    var success = function(pos) {
      $('.map-container').fadeIn('slow');

      var crd = pos.coords;
      console.log('Your current position is:');
      console.log('Latitude : ' + crd.latitude);
      console.log('Longitude: ' + crd.longitude);
      console.log('More or less ' + crd.accuracy + ' meters.');

      $('input[name=latitude]').val(crd.latitude);
      $('input[name=longitude]').val(crd.longitude);

      var loc = new google.maps.LatLng(crd.latitude, crd.longitude);

      var map = new google.maps.Map($('[data-map]')[0], {
        center: loc,
        zoom: 18,
        draggable: false
      });

      var marker = new google.maps.Marker({
        map: map,
        position: loc,
        animation: google.maps.Animation.DROP,
        title: 'You'
      });
    };

    var error = function(err) {
      $('.map-container').hide();
    };

    navigator.geolocation.getCurrentPosition(success, error);
  }
});