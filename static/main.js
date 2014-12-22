$(document).ready(function() {
  $('.hidden').hide().removeClass('hidden');

  $('[data-location]').each(function() {
    var container = $(this);
    var box = $('.map-box', container);
    var button = $('button[data-map-toggle]', container);
    box.hide();

    if (container.data('lat') == -10000) {
      button.hide();
      return;
    }

    button.click(function() {
      if (box.is(':visible')) {
        box.hide();
        button.removeClass('active');
      } else {
        box.show();
        button.addClass('active');

        var loc = new google.maps.LatLng(container.data('lat'), container.data('long'));

        var map = new google.maps.Map(box[0], {
          center: loc,
          zoom: 18
        });

        var marker = new google.maps.Marker({
          map: map,
          position: loc,
          animation: google.maps.Animation.DROP
        });
      }
    });
  });


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

  $('[data-tablesorter]').tablesorter();
  $('[data-toggle="tooltip"]').tooltip();
});